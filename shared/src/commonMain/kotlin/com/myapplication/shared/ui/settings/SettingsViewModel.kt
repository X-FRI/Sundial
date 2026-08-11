package com.myapplication.shared.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.data.sync.SyncEngine
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.sync.SyncConfig
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import com.myapplication.shared.util.createDeviceId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsForm(
    val mode: SyncMode = SyncMode.Local,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val sundialUrl: String = "",
)

class SettingsViewModel(
    private val repository: TodoRepository,
    private val engine: SyncEngine,
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = engine.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.initial)

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    private var formLoaded = false

    init {
        viewModelScope.launch {
            val settings = repository.getSettings().getOrNull() ?: emptyMap()
            _form.value = SettingsForm(
                mode = SyncMode.fromKey(settings["sync.mode"] ?: "local"),
                supabaseUrl = settings["sync.supabase.url"] ?: "",
                supabaseKey = settings["sync.supabase.key"] ?: "",
                sundialUrl = settings["sync.sundial.url"] ?: "",
            )
            formLoaded = true
        }
    }

    fun setMode(mode: SyncMode) {
        _form.value = _form.value.copy(mode = mode)
    }

    fun setSupabaseUrl(value: String) {
        _form.value = _form.value.copy(supabaseUrl = value)
    }

    fun setSupabaseKey(value: String) {
        _form.value = _form.value.copy(supabaseKey = value)
    }

    fun save() {
        if (!formLoaded) return
        val f = _form.value
        if (f.mode == SyncMode.Supabase && (f.supabaseUrl.isBlank() || f.supabaseKey.isBlank())) return
        viewModelScope.launch {
            val settings = mapOf(
                "sync.mode" to when (f.mode) {
                    SyncMode.Local -> "local"
                    SyncMode.Supabase -> "supabase"
                    SyncMode.SundialServer -> "sundial"
                },
                "sync.supabase.url" to f.supabaseUrl.trim(),
                "sync.supabase.key" to f.supabaseKey.trim(),
                "sync.sundial.url" to f.sundialUrl.trim(),
            )
            settings.forEach { (k, v) -> repository.setSetting(k, v).onLeft { } }
            val deviceId = repository.getSetting("sync.deviceId").getOrNull()
                ?: run {
                    val id = createDeviceId()
                    repository.setSetting("sync.deviceId", id)
                    id
                }
            engine.configure(
                SyncConfig(
                    mode = f.mode,
                    supabaseUrl = f.supabaseUrl.trim(),
                    supabaseKey = f.supabaseKey.trim(),
                    sundialUrl = f.sundialUrl.trim(),
                    deviceId = deviceId,
                ),
            )
        }
    }
}
