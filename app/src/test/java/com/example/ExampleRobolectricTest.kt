package com.example

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.SynFixViolet
import com.example.ui.theme.generateColorSchemeFromSeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SynFix", appName)
    }

    @Test
    fun `dark and amoled color schemes have high contrast text`() {
        val dark = generateColorSchemeFromSeed(SynFixViolet, darkTheme = true, amoledMode = false)
        val amoled = generateColorSchemeFromSeed(SynFixViolet, darkTheme = true, amoledMode = true)
        val light = generateColorSchemeFromSeed(SynFixViolet, darkTheme = false, amoledMode = false)

        assertTrue("Dark onBackground must be bright", dark.onBackground.luminance() > 0.7f)
        assertTrue("AMOLED background must be pure black", amoled.background == Color(0xFF000000))
        assertTrue("AMOLED onSurface must be bright", amoled.onSurface.luminance() > 0.7f)
        assertTrue("Light onBackground must be dark", light.onBackground.luminance() < 0.2f)
    }
}
