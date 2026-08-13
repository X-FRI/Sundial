package com.myapplication.shared.ui.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LaunchEffectKeyTest {
    @Test
    fun repeatedTargetWithNewNonceIsDistinctLaunchEvent() {
        assertNotEquals(
            launchEffectKey("today", 1),
            launchEffectKey("today", 2),
        )
    }

    @Test
    fun unchangedTargetAndNonceIsSameLaunchEvent() {
        assertEquals(
            launchEffectKey("today", 1),
            launchEffectKey("today", 1),
        )
    }
}
