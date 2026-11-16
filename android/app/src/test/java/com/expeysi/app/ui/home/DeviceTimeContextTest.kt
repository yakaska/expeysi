package com.expeysi.app.ui.home

import android.app.Application
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * deviceUses24Hour: явная настройка TIME_12_24 главнее локали. Локаль Robolectric
 * по умолчанию — en-US (12h), поэтому без настройки правило локали даёт 12 часов.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DeviceTimeContextTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, null)
    }

    @Test
    fun `настройка 24 главнее 12-часовой локали en`() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "24")
        assertTrue(deviceUses24Hour(context))
    }

    @Test
    fun `настройка 12 даёт 12 часов`() {
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, "12")
        assertFalse(deviceUses24Hour(context))
    }

    @Test
    fun `настройка не задана - решение по локали en 12 часов`() {
        assertFalse(deviceUses24Hour(context))
    }
}
