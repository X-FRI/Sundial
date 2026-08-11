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

/**
 * 同步引擎：负责 SyncClient 的生命周期与两条后台循环（push / remote）。
 *
 * 生命周期约定：
 * - configure 每次都会先停掉旧 client 与旧循环再建新（幂等，可反复切换模式）；
 * - Local 模式只建 NoopSyncClient、不启动循环——Noop 的 push 恒成功，
 *   引擎不跑循环 outbox 也不会堆积；
 * - status 是唯一对外暴露的状态源（StateFlow），UI 直接订阅。
 */
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

    /**
     * 应用新同步配置：1. 停掉旧 client/循环；2. 按配置建新 client；
     * 3. 仅非 Local 模式启动 push/remote 循环。
     */
    fun configure(newConfig: SyncConfig) {
        // 1. 先停旧（取消循环 + 异步 close 旧 client），再建新，避免新旧并存
        stopCurrent()
        _status.value = _status.value.copy(mode = newConfig.mode, connected = false)
        SyncClientFactory.create(newConfig).fold(
            ifLeft = { error ->
                // 配置失败（如缺 URL/Key）：退回 Noop，保本地功能可用
                client = NoopSyncClient()
                _status.value = SyncStatus(newConfig.mode, false, 0, _status.value.lastSyncAt, error.message())
            },
            ifRight = { newClient ->
                client = newClient
                if (newConfig.mode == SyncMode.Local) {
                    // Local 模式无远端，不建 coordinator 也不起循环（outbox 由 Noop 吞掉）
                    coordinator = null
                } else {
                    coordinator = SyncCoordinator(repository, newClient, newConfig.deviceId)
                    startPushLoop()
                    startRemoteLoop()
                }
            },
        )
    }

    /**
     * 停掉当前同步：取消两个循环后，把旧 client 先取到局部变量再替换为 Noop，
     * 最后异步 close。注意必须先捕获 old 再覆盖 client——若先赋 Noop 就丢掉了
     * 需要关闭的引用（会关错对象/泄漏连接）。
     */
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

    /**
     * 推送循环：每 2 秒尝试 drainOutbox 一次（简单轮询，不需要触发器）。
     * 单次失败只记录状态（outbox 行保留，下轮重试）；任何异常都兜住并置
     * connected=false，但不中断循环。
     */
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

    private fun SyncError.message(): String = when (this) {
        is SyncError.NotConfigured -> "同步配置不完整"
        is SyncError.Transport -> message
    }
}
