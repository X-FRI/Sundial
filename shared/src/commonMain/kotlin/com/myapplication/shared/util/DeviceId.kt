package com.myapplication.shared.util

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun createDeviceId(): String = Uuid.random().toString()
