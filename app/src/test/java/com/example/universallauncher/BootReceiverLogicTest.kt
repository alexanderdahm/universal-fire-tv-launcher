package com.example.universallauncher

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The decisions [BootReceiver] makes, tested without the receiver itself.
 *
 * Limitation: `BroadcastReceiver.onReceive()` needs a real `Context`, a
 * `PackageManager` and a main `Looper`, none of which exist in a local JVM
 * test, and the project deliberately has neither Robolectric nor an
 * instrumentation test setup. Everything the receiver decides is therefore
 * factored out into the plain functions exercised below - action filtering,
 * the duplicate broadcast guard and the package resolution - so the part left
 * untested is only the wiring: reading the intent, `goAsync()` and the
 * `postDelayed()` call.
 */
class BootReceiverLogicTest {

    private val settings = AutostartSettings(FakeSharedPreferences())

    @Test
    fun `only the boot broadcast is acted upon`() {
        assertTrue(isBootCompletedAction(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(isBootCompletedAction(Intent.ACTION_PACKAGE_REMOVED))
        assertFalse(isBootCompletedAction(null))
        assertFalse(isBootCompletedAction(""))
    }

    @Test
    fun `a repeated boot broadcast starts nothing a second time`() {
        val guard = OneShotGuard()

        assertTrue(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
        assertFalse(guard.tryAcquire())
    }

    @Test
    fun `the receiver starts nothing while autostart is disabled`() {
        assertNull(resolveAutostartPackage(installed = setOf("com.example.televizo")))
    }

    @Test
    fun `the receiver starts the stored package, not the launcher itself`() {
        settings.setAutostartApp("com.example.televizo")

        assertEquals(
            "com.example.televizo",
            resolveAutostartPackage(installed = setOf("com.example.televizo", "org.xbmc.kodi"))
        )
    }

    @Test
    fun `the receiver starts the app picked last`() {
        settings.setAutostartApp("com.example.televizo")
        settings.setAutostartApp("org.xbmc.kodi")

        assertEquals(
            "org.xbmc.kodi",
            resolveAutostartPackage(installed = setOf("com.example.televizo", "org.xbmc.kodi"))
        )
    }

    @Test
    fun `an invalid package neither starts anything nor throws`() {
        settings.setAutostartApp("com.example.uninstalled")

        assertNull(resolveAutostartPackage(installed = emptySet()))
        // ... and the next boot does not even look for it any more.
        assertFalse(settings.isEnabled)
        assertNull(resolveAutostartPackage(installed = emptySet()))
    }

    /**
     * What `BootReceiver.onReceive()` does with the settings, with the
     * `PackageManager` lookup replaced by a fixed set of installed packages.
     */
    private fun resolveAutostartPackage(installed: Set<String>): String? =
        settings.launchablePackage { candidate -> candidate in installed }
}
