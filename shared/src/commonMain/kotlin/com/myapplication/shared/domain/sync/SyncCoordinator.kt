package com.myapplication.shared.domain.sync

import arrow.core.Either
import arrow.core.raise.either
import com.myapplication.shared.domain.error.TodoError
import com.myapplication.shared.domain.repository.TodoRepository
import kotlinx.serialization.json.Json

class SyncCoordinator(
    private val repository: TodoRepository,
    private val client: SyncClient,
    private val deviceId: String,
) {

    suspend fun drainOutbox(): Either<SyncError, Int> = either {
        val rows = repository.readOutbox(100).mapLeftToSync().bind()
        if (rows.isEmpty()) return@either 0
        client.push(rows).bind()
        repository.clearOutbox(rows.last().seq).mapLeftToSync().bind()
        rows.size
    }

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
