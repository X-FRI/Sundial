package com.myapplication.shared.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myapplication.shared.data.sync.SyncEngine
import com.myapplication.shared.domain.repository.TodoRepository
import com.myapplication.shared.domain.settings.SaveSettingsInput
import com.myapplication.shared.domain.settings.SaveSettingsUseCase
import com.myapplication.shared.domain.settings.SettingsError
import com.myapplication.shared.domain.settings.toSettingsError
import com.myapplication.shared.domain.sync.SyncMode
import com.myapplication.shared.domain.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 设置页的表单状态（与 SettingsScreen 直接映射）。
 *
 * 注意：sync.mode 的持久化 key 是字符串，但 UI 层全程使用 [SyncMode] 枚举，
 * 转换边界只在本文件（存时 key 转字符串、载入时反向解析）。
 */
data class SettingsForm(
    val mode: SyncMode = SyncMode.Local,
    val supabaseUrl: String = "",
    val supabaseKey: String = "",
    val sundialUrl: String = "",
)

/**
 * 设置页 ViewModel：负责表单加载 / 编辑 / 保存三步。
 *
 * 关键设计：
 * - [formLoaded] 防覆盖竞态：表单初始值异步从数据库读取，在读取完成前用户
 *   若已开始编辑，[save] 直接忽略（避免用旧默认值覆盖已输入的内容）；
 * - 展示用 [syncStatus] 直接透传 SyncEngine 的状态流，便于 SettingsScreen 的
 *   StatusCard 展示连接 / 待同步 / 最近错误；
 * - [save] 是「持久化 → 重配置引擎」两步：先逐条写 settings，再以完整
 *   SyncConfig 调 [SyncEngine.configure]，让引擎按新配置重建连接。
 */
class SettingsViewModel(
    private val repository: TodoRepository,
    private val engine: SyncEngine,
    private val saveSettings: SaveSettingsUseCase,
) : ViewModel() {
    /** 同步引擎实时状态（连接与否、待同步条数、最近错误），供状态卡片展示。 */
    val syncStatus: StateFlow<SyncStatus> =
        engine.status
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.initial)

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    private val _preferences = MutableStateFlow(SettingsPreferences())
    val preferences: StateFlow<SettingsPreferences> = _preferences

    private val _lastSettingsError = MutableStateFlow<SettingsError?>(null)
    val lastSettingsError: StateFlow<SettingsError?> = _lastSettingsError

    /** 持久化表单是否已加载完成；为 false 时 save() 一律拒绝。 */
    private var formLoaded = false
    private var preferencesLoaded = false

    init {
        // 异步加载已保存的设置并回填表单；读取失败时保留默认 UI 值，
        // 但不置 loaded，避免后续 save() 用默认值覆盖未知的已保存设置。
        viewModelScope.launch {
            repository.getSettings().fold(
                ifLeft = {
                    _lastSettingsError.value = SettingsError.Persistence("读取设置失败")
                },
                ifRight = { settings ->
                    _form.value =
                        SettingsForm(
                            mode = SyncMode.fromKey(settings["sync.mode"] ?: "local"),
                            supabaseUrl = settings["sync.supabase.url"] ?: "",
                            supabaseKey = settings["sync.supabase.key"] ?: "",
                            sundialUrl = settings["sync.sundial.url"] ?: "",
                        )
                    _preferences.value = SettingsPreferences.fromSettingsMap(settings)
                    _lastSettingsError.value = null
                    formLoaded = true
                    preferencesLoaded = true
                },
            )
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

    fun setThemeMode(mode: ThemeMode) {
        updatePreferences(_preferences.value.copy(themeMode = mode))
    }

    fun setDisplayDensity(density: DisplayDensity) {
        updatePreferences(_preferences.value.copy(displayDensity = density))
    }

    fun setFontFamily(value: String) {
        updatePreferences(_preferences.value.copy(fontFamily = value))
    }

    private fun updatePreferences(next: SettingsPreferences) {
        _preferences.value = next
        if (!preferencesLoaded) return
        viewModelScope.launch {
            next.toSettingsMap().forEach { (key, value) ->
                repository.setSetting(key, value).onLeft { error ->
                    _lastSettingsError.value = error.toSettingsError()
                }
            }
        }
    }

    /**
     * 保存配置：由 SaveSettingsUseCase 负责校验、持久化与 deviceId 装配；
     * ViewModel 只处理 UI 错误状态与同步引擎重配置。
     */
    fun save() {
        if (!formLoaded) return
        viewModelScope.launch {
            saveSettings(_form.value.toSaveSettingsInput()).fold(
                ifLeft = { error ->
                    _lastSettingsError.value = error
                },
                ifRight = { config ->
                    _lastSettingsError.value = null
                    engine.configure(config)
                },
            )
        }
    }
}

private fun SettingsForm.toSaveSettingsInput(): SaveSettingsInput =
    SaveSettingsInput(
        mode = mode,
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
        sundialUrl = sundialUrl,
    )
