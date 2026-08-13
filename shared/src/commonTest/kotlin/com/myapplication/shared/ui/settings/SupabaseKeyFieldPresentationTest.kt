package com.myapplication.shared.ui.settings

import com.myapplication.shared.ui.components.IconName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SupabaseKeyFieldPresentationTest {
    @Test
    fun supabaseAnonKeyIsHiddenByDefault() {
        val presentation = supabaseKeyFieldPresentation(revealed = false)

        assertTrue(presentation.hidden)
        assertEquals(IconName.Eye, presentation.toggleIcon)
        assertEquals("显示 anon 公钥", presentation.toggleContentDescription)
    }

    @Test
    fun revealedSupabaseAnonKeyOffersHideAction() {
        val presentation = supabaseKeyFieldPresentation(revealed = true)

        assertEquals(false, presentation.hidden)
        assertEquals(IconName.EyeOff, presentation.toggleIcon)
        assertEquals("隐藏 anon 公钥", presentation.toggleContentDescription)
    }
}
