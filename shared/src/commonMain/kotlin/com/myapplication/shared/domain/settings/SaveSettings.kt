package com.myapplication.shared.domain.settings

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.myapplication.shared.domain.repository.SettingsStore
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.ui.settings.SettingsForm

class SaveSettingsUseCase(
    private val settings: SettingsStore,
    private val createDeviceId: () -> String,
) {
    suspend operator fun invoke(input: SettingsForm): Either<SettingsError, SyncConfig> =
        either {
            val supabaseUrl = input.supabaseUrl.trim()
            val supabaseKey = input.supabaseKey.trim()
            val sundialUrl = input.sundialUrl.trim()

            ensure(input.mode != SyncMode.Supabase || (supabaseUrl.isNotBlank() && supabaseKey.isNotBlank())) {
                SettingsError.MissingSupabaseConfig
            }

            val deviceId =
                settings.getSetting("sync.deviceId").mapLeft { it.toSettingsError() }.bind()
                    ?: createDeviceId().also { generated ->
                        settings.setSetting("sync.deviceId", generated).mapLeft { it.toSettingsError() }.bind()
                    }

            settings.setSetting("sync.mode", input.mode.toSettingsKey()).mapLeft { it.toSettingsError() }.bind()
            settings.setSetting("sync.supabase.url", supabaseUrl).mapLeft { it.toSettingsError() }.bind()
            settings.setSetting("sync.supabase.key", supabaseKey).mapLeft { it.toSettingsError() }.bind()
            settings.setSetting("sync.sundial.url", sundialUrl).mapLeft { it.toSettingsError() }.bind()

            SyncConfig(
                mode = input.mode,
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseKey,
                sundialUrl = sundialUrl,
                deviceId = deviceId,
            )
        }
}

private fun SyncMode.toSettingsKey(): String =
    when (this) {
        SyncMode.Local -> "local"
        SyncMode.Supabase -> "supabase"
        SyncMode.SundialServer -> "sundial"
    }
