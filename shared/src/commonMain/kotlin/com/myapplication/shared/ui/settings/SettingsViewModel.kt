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
) : ViewModel() {

    /** 同步引擎实时状态（连接与否、待同步条数、最近错误），供状态卡片展示。 */
    val syncStatus: StateFlow<SyncStatus> = engine.status
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.initial)

    private val _form = MutableStateFlow(SettingsForm())
    val form: StateFlow<SettingsForm> = _form

    /** 持久化表单是否已加载完成；为 false 时 save() 一律拒绝。 */
    private var formLoaded = false

    init {
        // 异步加载已保存的设置并回填表单；读取失败（getOrNull() == null）时
        // 退回全部默认值（本地模式），并照常置 formLoaded = true。
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

    /**
     * 保存配置。两步：
     * 1. 持久化：逐条写入 settings（同步模式、URL、key），此处忽略单个写入失败；
     * 2. 应用：确保 deviceId 存在（没有就生成并持久化），再把完整 SyncConfig
     *    交给引擎 configure，触发引擎按新配置（重新）连接。
     * 前置校验：表单未加载完成或 Supabase 配置不完整时直接返回，不落库。
     */
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
            // deviceId 是同步客户端身份标识，只生成一次；重启后从库里取回。
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
