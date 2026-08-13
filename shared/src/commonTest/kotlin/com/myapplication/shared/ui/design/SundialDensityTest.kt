package com.myapplication.shared.ui.design

import kotlin.test.Test
import kotlin.test.assertEquals

class SundialDensityTest {
    @Test
    fun desktopPanelWidthsAreStable() {
        assertEquals(272, SundialLayout.sidebarWidthDp)
        assertEquals(420, SundialLayout.inspectorWidthDp)
        assertEquals(720, SundialLayout.compactBreakpointDp)
    }

    @Test
    fun rowHeightsSeparateDesktopAndTouch() {
        assertEquals(40, SundialDensity.compactTaskRowDp)
        assertEquals(52, SundialDensity.touchTaskRowDp)
        assertEquals(36, SundialDensity.toolbarControlDp)
    }
}
