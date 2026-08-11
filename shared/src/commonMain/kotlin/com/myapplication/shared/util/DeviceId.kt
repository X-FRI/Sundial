package com.myapplication.shared.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 生成本设备唯一 ID（UUID v4 字符串），用于同步层的设备标识
 * （updatedBy / sync.deviceId 设置项）。
 *
 * 说明：UUID 不依赖平台 API，跨平台（Android/iOS/Desktop）行为一致；
 * 仅在 AppGraph 首次初始化 repository 时调用一次并持久化到数据库。
 */
@OptIn(ExperimentalUuidApi::class)
internal fun createDeviceId(): String = Uuid.random().toString()
