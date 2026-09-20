package com.example.universallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the whole autostart configuration: storing it, replacing it, clearing
 * it, and the cleanup of a package that no longer exists.
 */
class AutostartSettingsTest {

    private val preferences = FakeSharedPreferences()
    private val settings = AutostartSettings(preferences)

    @Test
    fun `no autostart is configured initially`() {
        assertFalse(settings.isEnabled)
        assertNull(settings.packageName)
        assertFalse(settings.isAutostartApp("com.example.televizo"))
    }

    @Test
    fun `autostart can be enabled for an app`() {
        settings.setAutostartApp("com.example.televizo")

        assertTrue(settings.isEnabled)
        assertTrue(settings.isAutostartApp("com.example.televizo"))
    }

    @Test
    fun `package name is stored in the preferences`() {
        settings.setAutostartApp("com.example.televizo")

        assertEquals("com.example.televizo", settings.packageName)
        assertEquals("com.example.televizo", preferences.getString(AutostartSettings.KEY_PACKAGE, null))
        assertTrue(preferences.getBoolean(AutostartSettings.KEY_ENABLED, false))
    }

    @Test
    fun `autostart can be disabled again`() {
        settings.setAutostartApp("com.example.televizo")

        settings.disable()

        assertFalse(settings.isEnabled)
        assertNull(settings.packageName)
        assertFalse(preferences.getBoolean(AutostartSettings.KEY_ENABLED, false))
        assertNull(preferences.getString(AutostartSettings.KEY_PACKAGE, null))
    }

    @Test
    fun `picking a second app replaces the first one`() {
        settings.setAutostartApp("com.example.televizo")
        settings.setAutostartApp("org.xbmc.kodi")

        assertEquals("org.xbmc.kodi", settings.packageName)
        assertFalse(settings.isAutostartApp("com.example.televizo"))
        assertTrue(settings.isAutostartApp("org.xbmc.kodi"))
    }

    @Test
    fun `a stored package without the enabled flag counts as disabled`() {
        preferences.edit().putString(AutostartSettings.KEY_PACKAGE, "com.example.televizo").apply()

        assertFalse(settings.isEnabled)
        assertNull(settings.packageName)
    }

    @Test
    fun `a blank stored package counts as no configuration`() {
        preferences.edit()
            .putBoolean(AutostartSettings.KEY_ENABLED, true)
            .putString(AutostartSettings.KEY_PACKAGE, "   ")
            .apply()

        assertFalse(settings.isEnabled)
        assertNull(settings.packageName)
    }

    @Test
    fun `launchablePackage returns the configured package when it is installed`() {
        settings.setAutostartApp("com.example.televizo")

        assertEquals("com.example.televizo", settings.launchablePackage { it == "com.example.televizo" })
    }

    @Test
    fun `launchablePackage returns nothing while autostart is disabled`() {
        var asked = false

        assertNull(settings.launchablePackage { asked = true; true })
        assertFalse("A disabled autostart must not resolve anything", asked)
    }

    @Test
    fun `an uninstalled package is dropped from the configuration`() {
        settings.setAutostartApp("com.example.televizo")

        // The app is gone: nothing to start, and the stale entry is cleared.
        assertNull(settings.launchablePackage { false })
        assertFalse(settings.isEnabled)
        assertNull(settings.packageName)
        assertNull(preferences.getString(AutostartSettings.KEY_PACKAGE, null))
    }

    @Test
    fun `an app without a launcher intent is dropped as well`() {
        settings.setAutostartApp("com.example.headless")

        // Installed, but nothing that AppLauncher could resolve an intent for.
        assertNull(settings.launchablePackage { packageName -> packageName != "com.example.headless" })
        assertFalse(settings.isEnabled)
    }
}
