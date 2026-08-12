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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 同步引擎：负责 SyncClient 的生命周期、两条后台循环（push / remote）
 * 与对外状态（SyncStatus，UI 直接订阅）。
 *
 * 生命周期约定：
 * - configure 每次都会先停掉旧 client 与旧循环再建新（幂等，可反复切换模式）；
 * - Local 模式只建 NoopSyncClient、不启动循环——Noop 的 push 恒成功，
 *   引擎不跑循环 outbox 也不会堆积；
 * - status 是唯一对外暴露的状态源（StateFlow）；
 * - syncing 表示「正在同步」：outbox 有待推送或 syncNow 执行中时为 true，
 *   推送排空后复位为 false（驱动 UI 的同步动画）。
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
    private var backoffMs = backoffBaseMs

    private var client: SyncClient = NoopSyncClient()
    private var coordinator: SyncCoordinator? = null
    private var pushJob: Job? = null
    private var remoteJob: Job? = null
    private var statusJob: Job? = null
    private var statusJob2: Job? = null
    private var syncNowJob: Job? = null

    /**
     * 应用新同步配置：1. 停掉旧 client/循环；2. 按配置建新 client；
     * 3. 仅非 Local 模式启动 push/remote/状态循环并立即对齐一次。
     */
    fun configure(newConfig: SyncConfig) {
        // 1. 先停旧（取消循环 + 异步 close 旧 client），再建新，避免新旧并存
        stopCurrent()
        _status.value = _status.value.copy(mode = newConfig.mode, connected = false, syncing = false)
        clientFactory(newConfig).fold(
            ifLeft = { error ->
                // 配置失败（如缺 URL/Key）：退回 Noop，保本地功能可用；
                // pendingCount 保留现值，避免失败切换把待推送数清零（失真）
                client = NoopSyncClient()
                _status.value = SyncStatus(
                    newConfig.mode, false, _status.value.pendingCount,
                    _status.value.lastSyncAt, error.message(), syncing = false,
                )
            },
            ifRight = { newClient ->
                client = newClient
                if (newConfig.mode == SyncMode.Local) {
                    // Local 模式无远端，不建 coordinator 也不起循环（outbox 由 Noop 吞掉）
                    coordinator = null
                } else {
                    coordinator = SyncCoordinator(repository, newClient, newConfig.deviceId)
                    backoffMs = backoffBaseMs
                    startPushLoop()
                    startRemoteLoop()
                    startStatusWatchers()
                    syncNow()              // 首次启用自动对齐
                }
            },
        )
    }

    /**
     * 立即同步：推本地 outbox + 拉远端全量，供下拉刷新/侧边栏手动触发。
     * 幂等：coordinator 缺失（Local/未配置）或上一次 syncNow 仍在跑时直接返回。
     * 异常兜底：syncing=true 之后的整段逻辑都在 try/catch 内，任何意外异常
     * （含 observeOutboxCount 抛错）都会复位 syncing=false 并记录 lastError，
     * 保证 UI 同步动画不会卡死在 true。
     */
    fun syncNow() {
        if (coordinator == null || syncNowJob?.isActive == true) return
        syncNowJob = scope.launch {
            _status.update { it.copy(syncing = true) }
            try {
                val drainResult = tryDrainOutbox()
                val pullResult = tryPullFromRemote()
                val drainFailed = drainResult?.isLeft() == true
                val pullFailed = pullResult?.isLeft() == true
                if (drainFailed || pullFailed) {
                    val msg = when {
                        drainFailed -> (drainResult as Either.Left<SyncError>).value.message()
                        else -> (pullResult as Either.Left<SyncError>).value.message()
                    }
                    _status.update { it.copy(syncing = false, connected = false, lastError = msg) }
                } else {
                    val pending = repository.observeOutboxCount().first()
                    _status.update {
                        it.copy(
                            syncing = pending > 0,
                            connected = true,
                            pendingCount = pending,
                            lastSyncAt = clock.now().toEpochMilliseconds(),
                            lastError = null,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _status.update { it.copy(syncing = false, connected = false, lastError = "同步失败: ${e.message}") }
            }
        }
    }

    /** drainOutbox 的安全包装：业务异常吞掉返回 null（交给成功分支重查 pending），
     *  但 CancellationException 必须重抛，保证协程可被干净取消。 */
    private suspend fun tryDrainOutbox(): Either<SyncError, Int>? = try {
        coordinator?.drainOutbox()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** pullFromRemote 的安全包装，语义同 [tryDrainOutbox]。 */
    private suspend fun tryPullFromRemote(): Either<SyncError, Int>? = try {
        coordinator?.pullFromRemote()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * 停掉当前同步：取消全部循环后，把旧 client 先取到局部变量再替换为 Noop，
     * 最后异步 close。注意必须先捕获 old 再覆盖 client——若先赋 Noop 就丢掉了
     * 需要关闭的引用（会关错对象/泄漏连接）。
     */
    private fun stopCurrent() {
        pushJob?.cancel()
        remoteJob?.cancel()
        statusJob?.cancel()
        statusJob2?.cancel()
        syncNowJob?.cancel()
        pushJob = null
        remoteJob = null
        statusJob = null
        statusJob2 = null
        syncNowJob = null
        val old = client
        client = NoopSyncClient()
        coordinator = null
        scope.launch { runCatching { old.close() } }
    }

    /**
     * 推送循环：drainOutbox 轮询，失败时指数退避（2s→4s→8s→…→30s 封顶），
     * 成功后复位退避并刷新状态。单次失败只记录状态（outbox 行保留，下轮重试）；
     * 任何异常都兜住并置 connected=false，但不中断循环。
     */
    private fun startPushLoop() {
        pushJob = scope.launch {
            while (isActive) {
                try {
                    when (val result = coordinator?.drainOutbox()) {
                        null -> Unit
                        is Either.Left -> {
                            _status.update {
                                it.copy(connected = false, lastError = result.value.message(), syncing = false)
                            }
                            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                        }
                        is Either.Right -> {
                            backoffMs = backoffBaseMs
                            val pending = repository.observeOutboxCount().first()
                            _status.update {
                                it.copy(
                                    connected = true,
                                    pendingCount = pending,
                                    syncing = pending > 0,
                                    lastSyncAt = clock.now().toEpochMilliseconds(),
                                    lastError = null,
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _status.update { it.copy(connected = false, lastError = "同步失败: ${e.message}", syncing = false) }
                    backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
                }
                delay(backoffMs)
            }
        }
    }

    /**
     * 远端循环：消费 observeRemoteChanges 流。
     * - retryWhen 处理断线：非取消错误记录状态并等 2 秒重连（重订阅）；
     * - collect 内每行 try/catch：单行应用失败不影响后续行；
     * - CancellationException 一律重抛，保证协程可被干净取消。
     */
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

    /**
     * 状态观察循环：
     * - outbox 计数：有本地新写（count > 0）且当前未在同步时，置 syncing=true
     *   驱动动画，推送排空后由 push 循环复位；
     * - 连接健康度：跟随 client.observeConnectionStatus（Realtime 真实状态），
     *   push 成功置 connected=true 作为状态滞后时的兜底。
     */
    private fun startStatusWatchers() {
        statusJob = scope.launch {
            repository.observeOutboxCount().collect { count ->
                if (count > 0 && !_status.value.syncing) {
                    _status.update { it.copy(syncing = true) }
                }
            }
        }
        statusJob2 = scope.launch {
            client.observeConnectionStatus().collect { connected ->
                _status.update { it.copy(connected = connected) }
            }
        }
    }

    private fun SyncError.message(): String = when (this) {
        is SyncError.NotConfigured -> "同步配置不完整"
        is SyncError.Transport -> message
    }
}
