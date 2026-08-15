package com.myapplication.shared.domain.settings

import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.test.FakeTodoRepository
import com.myapplication.shared.ui.settings.SettingsForm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SaveSettingsUseCaseTest {
    @Test
    fun supabaseMissingUrlOrKeyReturnsMissingSupabaseConfig() =
        runTest {
            val repository = FakeTodoRepository()
            val useCase = SaveSettingsUseCase(repository) { "device-1" }

            val result =
                useCase(
                    SettingsForm(
                        mode = SyncMode.Supabase,
                        supabaseUrl = " ",
                        supabaseKey = "key",
                    ),
                )

            assertEquals(SettingsError.MissingSupabaseConfig, result.leftOrNull())
            assertEquals(emptyMap(), repository.settingsState.value)
        }

    @Test
    fun savePersistsTrimmedSettingsGeneratedDeviceIdAndReturnsConfig() =
        runTest {
            val repository = FakeTodoRepository()
            val useCase = SaveSettingsUseCase(repository) { "generated-device" }

            val result =
                useCase(
                    SettingsForm(
                        mode = SyncMode.Supabase,
                        supabaseUrl = " https://example.supabase.co ",
                        supabaseKey = " secret-key ",
                        sundialUrl = " https://sundial.example ",
                    ),
                )

            assertEquals(
                mapOf(
                    "sync.deviceId" to "generated-device",
                    "sync.mode" to "supabase",
                    "sync.supabase.url" to "https://example.supabase.co",
                    "sync.supabase.key" to "secret-key",
                    "sync.sundial.url" to "https://sundial.example",
                ),
                repository.settingsState.value,
            )
            assertEquals(
                SyncMode.Supabase,
                result.getOrNull()?.mode,
            )
            assertEquals("https://example.supabase.co", result.getOrNull()?.supabaseUrl)
            assertEquals("secret-key", result.getOrNull()?.supabaseKey)
            assertEquals("https://sundial.example", result.getOrNull()?.sundialUrl)
            assertEquals("generated-device", result.getOrNull()?.deviceId)
        }
}
