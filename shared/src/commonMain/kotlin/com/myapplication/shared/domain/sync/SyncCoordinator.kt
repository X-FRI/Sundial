package com.myapplication.shared.domain.sync

import arrow.core.Either
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.serialization.json.Json

/**
 * 同步协调器：把 outbox（本地→远端）与远端变更（远端→本地）两条通路串起来。
 *
 * 设计要点：
 * - 所有错误统一归一为 SyncError.Transport 这一个通道（单一错误通道），
 *   SyncEngine 只关心「失败 + 消息」，不关心错误来源；
 * - 远端到本地的行应用经由 applyRemote*，绝不写 outbox（防 ping-pong）。
 */
class SyncCoordinator(
    private val repository: TodoRepository,
    private val client: SyncClient,
    private val deviceId: String,
) {

    /**
     * 推送一批 outbox 行并清除水位线。返回本次推送行数（0 表示无待推送）。
     *
     * 三步流水线，水位线语义保证不丢不重：
     * 1. 读最多 100 条（按 seq 升序）；
     * 2. 整批推给远端（任一失败则整批回滚，outbox 原样保留）；
     * 3. 按最后一条的 seq 清水位线——由于 seq 单调递增且本地命令都在
     *    同一事务内追加，push 成功后这批之前的行必然已推送过，不会重复推。
     */
    suspend fun drainOutbox(): Either<SyncError, Int> = either {
        val rows = repository.readOutbox(100).mapLeftToSync().bind()
        if (rows.isEmpty()) return@either 0
        client.push(rows).bind()
        repository.clearOutbox(rows.last().seq).mapLeftToSync().bind()
        rows.size
    }

    /**
     * 全量拉取远端状态并逐行应用（复用 [applyRemote] 的 LWW 与回声过滤）。
     *
     * 应用前先过滤掉本设备写出的回声行（updatedBy == deviceId），保证返回值
     * 是「真实应用的远端行数」；逐行应用失败只影响该行（applyRemote 内部已把
     * 解析/持久化错误归一为 SyncError.Transport 并跳过），不中断整体。
     * 调用方（SyncEngine.syncNow）可据此感知远端是否有新数据。
     */
    suspend fun pullFromRemote(): Either<SyncError, Int> = either {
        val rows = client.pull().bind()
        var applied = 0
        rows.filter { it.updatedBy != deviceId }.forEach { row ->
            applyRemote(row).onRight { applied++ }
        }
        applied
    }

    /**
     * 应用一条远端变更到本地。行内已含 updatedBy，用于回声过滤。
     *
     * 处理顺序：
     * 1. 回声过滤：远端行由本设备写入（updatedBy == deviceId）时直接跳过——
     *    否则自己的写操作经 outbox 推送后又被远端回放回来，会覆盖本地最新状态；
     * 2. 按表分发：todo 与 reminder_list 各自反序列化为对应 DTO；
     *    解码失败（payload 损坏/版本不兼容）统一转 SyncError.Transport，
     *    保持单一错误通道；
     * 3. DELETE 不做表内分发，直接按表名 + rowId 应用（LWW 由 SQL 层保证）。
     */
    suspend fun applyRemote(row: SyncRow): Either<SyncError, Unit> = either {
        if (row.updatedBy == deviceId) return@either
        when (row.action) {
            SyncAction.UPSERT -> when (row.table) {
                "todo" -> {
                    val dto = try {
                        Json.decodeFromString<TodoRowDto>(row.payload ?: "")
                    } catch (e: Exception) {
                        raise(SyncError.Transport("解析远端 todo 行失败: ${e.message}"))
                    }
                    repository.applyRemoteUpsert(dto).mapLeftToSync().bind()
                }
                "reminder_list" -> {
                    val dto = try {
                        Json.decodeFromString<ListRowDto>(row.payload ?: "")
                    } catch (e: Exception) {
                        raise(SyncError.Transport("解析远端列表行失败: ${e.message}"))
                    }
                    repository.applyRemoteUpsertList(dto).mapLeftToSync().bind()
                }
                else -> Unit
            }
            SyncAction.DELETE -> repository.applyRemoteDelete(row.table, row.rowId, row.updatedAt).mapLeftToSync().bind()
        }
    }

    private fun <A> Either<TodoError, A>.mapLeftToSync(): Either<SyncError, A> =
        mapLeft { SyncError.Transport((it as? TodoError.Persistence)?.message ?: "本地读取失败") }
}
