package com.myapplication.widget

import android.content.Context
import com.myapplication.shared.domain.widget.TodayWidgetSnapshot
import java.io.File
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

    fun read(): TodayWidgetSnapshot? =
        runCatching {
            if (!file.exists()) return null
            json.decodeFromString<TodayWidgetSnapshot>(file.readText())
        }.getOrNull()

    fun write(snapshot: TodayWidgetSnapshot) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(snapshot))
    }
}
