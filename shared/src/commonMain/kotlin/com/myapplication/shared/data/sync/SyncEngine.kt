package com.myapplication.shared.data.sync

import arrow.core.Either
import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.Resource
import arrow.fx.coroutines.allocate
import arrow.fx.coroutines.bracket
import arrow.fx.coroutines.guarantee
import arrow.fx.coroutines.resource
import arrow.resilience.Schedule
import arrow.resilience.retry
import arrow.resilience.retryEither
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncCoordinator
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.domain.sync.configurationFailed
import com.myapplication.shared.domain.sync.configuring
import com.myapplication.shared.domain.sync.connectionObserved
import com.myapplication.shared.domain.sync.outboxObserved
import com.myapplication.shared.domain.sync.remoteApplied
import com.myapplication.shared.domain.sync.remoteApplyFailed
import com.myapplication.shared.domain.sync.remoteSubscriptionLost
import com.myapplication.shared.domain.sync.syncFailed
import com.myapplication.shared.domain.sync.syncStarted
import com.myapplication.shared.domain.sync.syncSucceeded
import com.myapplication.shared.domain.sync.userMessage
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 同步引擎：负责 SyncClient 生命周期、后台同步循环与 SyncStatus。
 *
 * 这里的边界刻意收敛成三层：
 * - Resource：描述一个同步 runtime（client + coordinator + jobs）如何释放；
 * - bracket：切换配置时先释放旧 runtime，再启动新 runtime；
 * - Schedule：描述 push/remote 的重试节奏，把退避策略从 while 循环里拿出来。
 */
class SyncEngine(
    private val scope: CoroutineScope,
    private val repository: TodoRepository,
    private val clock: Clock,
    private val clientFactory: (SyncConfig) -> Either<SyncError, SyncClient> = SyncClientFactory::create,
) {
    private val _status = MutableStateFlow(SyncStatus.initial)
    val status: StateFlow<SyncStatus> = _status

    private val backoffBaseMs = 2_000L
    private val maxBackoffMs = 30_000L

    private var activeRuntime: RuntimeLease? = null
    private var lifecycleJob: Job? = null
    private var syncNowJob: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun configure(newConfig: SyncConfig) {
        val previousLifecycle = lifecycleJob
        lifecycleJob = scope.launch {
            previousLifecycle?.join()
            releaseActiveRuntime()
            _status.update { it.configuring(newConfig.mode) }

            clientFactory(newConfig).fold(
                ifLeft = { error ->
                    _status.update { it.configurationFailed(newConfig.mode, error) }
                },
                ifRight = { newClient ->
                    val (runtime, release) = syncRuntimeResource(newConfig, newClient).allocate()
                    val lease = RuntimeLease(runtime, release)
                    activeRuntime = lease

                    if (runtime.coordinator != null) {
                        startPushLoop(runtime)
                        startRemoteLoop(runtime)
                        startStatusWatchers(runtime)
                        syncNow()
                    }
                },
            )
        }
    }

    /**
     * 手动同步：使用 guarantee 保证状态清理和 job 引用释放。
     */
    fun syncNow() {
        val runtime = activeRuntime?.runtime ?: return
        if (runtime.coordinator == null || syncNowJob?.isActive == true) return
        val job = scope.launch {
            guarantee(
                fa = { runSyncNowOnce(runtime) },
                finalizer = { syncNowJob = null },
            )
        }
        syncNowJob = job
        runtime.jobs += job
        job.invokeOnCompletion { runtime.jobs -= job }
    }

    private fun syncRuntimeResource(
        config: SyncConfig,
        client: SyncClient,
    ): Resource<SyncRuntime> =
        resource(
            acquire = {
                val coordinator = when (config.mode) {
                    SyncMode.Local -> null
                    else -> SyncCoordinator(repository, client, config.deviceId)
                }
                SyncRuntime(client, coordinator)
            },
            release = { runtime, _ ->
                runtime.cancelJobs()
                runtime.client.close()
            },
        )

    private suspend fun releaseActiveRuntime() {
        val lease = activeRuntime ?: return
        activeRuntime = null
        bracket(
            acquire = { lease },
            use = { it.runtime.cancelJobs() },
            release = { it.release(ExitCase.Completed) },
        )
    }

    private suspend fun runSyncNowOnce(runtime: SyncRuntime) {
        val coordinator = runtime.coordinator ?: return
        _status.update { it.syncStarted() }
        try {
            val drainResult = tryDrainOutbox(coordinator)
            val pullResult = tryPullFromRemote(coordinator)
            val drainFailed = drainResult?.isLeft() != false
            val pullFailed = pullResult?.isLeft() != false
            if (drainFailed || pullFailed) {
                val msg = when {
                    drainFailed -> (drainResult as? Either.Left<SyncError>)?.value?.userMessage()
                        ?: "同步失败: 未知错误"
                    else -> (pullResult as? Either.Left<SyncError>)?.value?.userMessage()
                        ?: "同步失败: 未知错误"
                }
                _status.update { it.syncFailed(msg) }
            } else {
                val pending = repository.observeOutboxCount().first()
                _status.update { it.syncSucceeded(pending, clock.now().toEpochMilliseconds()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _status.update { it.syncFailed("同步失败: ${e.message}") }
        }
    }

    private suspend fun tryDrainOutbox(coordinator: SyncCoordinator): Either<SyncError, Int>? = try {
        coordinator.drainOutbox()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun tryPullFromRemote(coordinator: SyncCoordinator): Either<SyncError, Int>? = try {
        coordinator.pullFromRemote()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private fun startPushLoop(runtime: SyncRuntime) {
        val coordinator = runtime.coordinator ?: return
        val job = scope.launch {
            while (isActive) {
                pushRetrySchedule().retryEither {
                    drainOutboxOnce(coordinator)
                }
                delay(backoffBaseMs)
            }
        }
        runtime.jobs += job
        job.invokeOnCompletion { runtime.jobs -= job }
    }

    private fun startRemoteLoop(runtime: SyncRuntime) {
        val coordinator = runtime.coordinator ?: return
        val job = scope.launch {
            runtime.client.observeRemoteChanges()
                .retry(remoteRetrySchedule())
                .collect { row ->
                    try {
                        when (val result = coordinator.applyRemote(row)) {
                            is Either.Left -> _status.update { it.remoteApplyFailed(result.value.userMessage()) }
                            is Either.Right -> {
                                val pending = repository.observeOutboxCount().first()
                                _status.update { it.remoteApplied(pending) }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _status.update { it.remoteApplyFailed(e.message ?: "未知错误") }
                    }
                }
        }
        runtime.jobs += job
        job.invokeOnCompletion { runtime.jobs -= job }
    }

    private fun startStatusWatchers(runtime: SyncRuntime) {
        val outboxJob = scope.launch {
            repository.observeOutboxCount().collect { count ->
                _status.update { it.outboxObserved(count) }
            }
        }
        val connectionJob = scope.launch {
            runtime.client.observeConnectionStatus().collect { connected ->
                _status.update { it.connectionObserved(connected) }
            }
        }
        runtime.jobs += outboxJob
        runtime.jobs += connectionJob
        outboxJob.invokeOnCompletion { runtime.jobs -= outboxJob }
        connectionJob.invokeOnCompletion { runtime.jobs -= connectionJob }
    }

    private suspend fun drainOutboxOnce(coordinator: SyncCoordinator): Either<SyncError, Unit> =
        try {
            when (val result = coordinator.drainOutbox()) {
                is Either.Left -> {
                    _status.update { it.syncFailed(result.value.userMessage()) }
                    Either.Left(result.value)
                }
                is Either.Right -> {
                    val pending = repository.observeOutboxCount().first()
                    _status.update { it.syncSucceeded(pending, clock.now().toEpochMilliseconds()) }
                    Either.Right(Unit)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = SyncError.Transport("同步失败: ${e.message}")
            _status.update { it.syncFailed(error.userMessage()) }
            Either.Left(error)
        }

    private fun pushRetrySchedule(): Schedule<SyncError, *> =
        Schedule.exponential<SyncError>((backoffBaseMs * 2).milliseconds)
            .doWhile { _, duration -> duration < maxBackoffMs.milliseconds }
            .andThen(Schedule.spaced<SyncError>(maxBackoffMs.milliseconds))

    private fun remoteRetrySchedule(): Schedule<Throwable, *> =
        Schedule.spaced<Throwable>(backoffBaseMs.milliseconds)
            .log { cause, _ ->
                if (cause !is CancellationException) {
                    _status.update { it.remoteSubscriptionLost(cause.message) }
                }
            }

    private suspend fun SyncRuntime.cancelJobs() {
        jobs.toList().forEach { it.cancelAndJoin() }
        jobs.clear()
        if (syncNowJob != null && syncNowJob?.isActive != true) {
            syncNowJob = null
        }
    }

    private data class RuntimeLease(
        val runtime: SyncRuntime,
        val release: suspend (ExitCase) -> Unit,
    )

    private data class SyncRuntime(
        val client: SyncClient,
        val coordinator: SyncCoordinator?,
        val jobs: MutableList<Job> = mutableListOf(),
    )
}
