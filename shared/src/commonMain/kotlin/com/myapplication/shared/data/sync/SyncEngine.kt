package com.myapplication.shared.data.sync

import arrow.core.Either
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.SyncClient
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncCoordinator
import com.myapplication.shared.domain.sync.SyncError
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SyncEngine(
    private val scope: CoroutineScope,
    private val repository: TodoRepository,
    private val clock: Clock,
) {
    private val _status = MutableStateFlow(SyncStatus.initial)
    val status: StateFlow<SyncStatus> = _status

    private var client: SyncClient = NoopSyncClient()
    private var coordinator: SyncCoordinator? = null
    private var pushJob: Job? = null
    private var remoteJob: Job? = null

    fun configure(newConfig: SyncConfig) {
        stopCurrent()
        _status.value = _status.value.copy(mode = newConfig.mode, connected = false)
        SyncClientFactory.create(newConfig).fold(
            ifLeft = { error ->
                client = NoopSyncClient()
                _status.value = SyncStatus(newConfig.mode, false, 0, _status.value.lastSyncAt, error.message())
            },
            ifRight = { newClient ->
                client = newClient
                if (newConfig.mode == SyncMode.Local) {
                    coordinator = null
                } else {
                    coordinator = SyncCoordinator(repository, newClient, newConfig.deviceId)
                    startPushLoop()
                    startRemoteLoop()
                }
            },
        )
    }

    private fun stopCurrent() {
        pushJob?.cancel()
        remoteJob?.cancel()
        pushJob = null
        remoteJob = null
        val old = client
        client = NoopSyncClient()
        coordinator = null
        scope.launch { runCatching { old.close() } }
    }

    private fun startPushLoop() {
        pushJob = scope.launch {
            while (isActive) {
                try {
                    when (val result = coordinator?.drainOutbox()) {
                        null -> Unit
                        is Either.Left -> _status.value = _status.value.copy(
                            connected = false,
                            lastError = result.value.message(),
                        )
                        is Either.Right -> _status.value = _status.value.copy(
                            connected = true,
                            pendingCount = repository.observeOutboxCount().first(),
                            lastSyncAt = clock.now().toEpochMilliseconds(),
                            lastError = null,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.value = _status.value.copy(connected = false, lastError = "同步失败: ${e.message}")
                }
                delay(2_000)
            }
        }
    }

    private fun startRemoteLoop() {
        remoteJob = scope.launch {
            client.observeRemoteChanges()
                .retryWhen { cause, _ ->
                    if (cause is CancellationException) throw cause
                    _status.value = _status.value.copy(connected = false, lastError = "实时订阅断开: ${cause.message}")
                    delay(2_000)
                    true
                }
                .collect { row ->
                    try {
                        when (val result = coordinator?.applyRemote(row)) {
                            null -> Unit
                            is Either.Left -> _status.value = _status.value.copy(lastError = "应用远端变更失败: ${result.value.message()}")
                            is Either.Right -> _status.value = _status.value.copy(
                                connected = true,
                                lastError = null,
                                pendingCount = repository.observeOutboxCount().first(),
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _status.value = _status.value.copy(lastError = "应用远端变更失败: ${e.message}")
                    }
                }
        }
    }

    private fun SyncError.message(): String = when (this) {
        is SyncError.NotConfigured -> "同步配置不完整"
        is SyncError.Transport -> message
    }
}
