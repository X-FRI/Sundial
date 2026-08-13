package com.myapplication.widget

import android.content.Context
import android.util.AtomicFile
import com.myapplication.shared.domain.widget.TodayWidgetSnapshot
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalSerializationApi::class)
internal class WidgetSnapshotCache(
    private val context: Context,
    private val json: Json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    },
) {
    private val file: File get() = File(context.filesDir, "sundial-widget/today-widget-snapshot.json")

    fun read(): TodayWidgetSnapshot? {
        return try {
            val atomicFile = AtomicFile(file)
            val bytes = atomicFile.openRead().use { stream -> stream.readBytes() }
            json.decodeFromString<TodayWidgetSnapshot>(bytes.decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    fun write(snapshot: TodayWidgetSnapshot) {
        file.parentFile?.mkdirs()
        val bytes = json.encodeToString(snapshot).encodeToByteArray()
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            atomicFile.finishWrite(stream)
        } catch (e: CancellationException) {
            atomicFile.failWrite(stream)
            throw e
        } catch (e: Exception) {
            atomicFile.failWrite(stream)
            throw e
        }
    }
}
