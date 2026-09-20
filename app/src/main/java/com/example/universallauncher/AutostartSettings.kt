package com.example.universallauncher

import android.content.Context
import android.content.SharedPreferences

/**
 * The one persisted user setting of this launcher: which installed app is
 * started automatically after the Fire TV has booted.
 *
 * At most one app can be configured, so picking another one replaces the
 * previous choice - there is a single key pair, never a list. Everything lives
 * in [SharedPreferences]; two booleans worth of state need no database.
 */
class AutostartSettings(private val preferences: SharedPreferences) {

    /** Production constructor; the primary one exists for tests. */
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    )

    /** `true` when autostart is switched on *and* a package is stored. */
    val isEnabled: Boolean
        get() = packageName != null

    /** The configured package, or `null` when autostart is off. */
    val packageName: String?
        get() = if (preferences.getBoolean(KEY_ENABLED, false)) storedPackage else null

    /** `true` when [packageName] is exactly the app currently set to autostart. */
    fun isAutostartApp(packageName: String): Boolean = this.packageName == packageName

    /** Makes [packageName] *the* autostart app, replacing any previous choice. */
    fun setAutostartApp(packageName: String) {
        preferences.edit()
            .putBoolean(KEY_ENABLED, true)
            .putString(KEY_PACKAGE, packageName)
            .apply()
    }

    /** Switches autostart off again and forgets the stored package. */
    fun disable() {
        preferences.edit()
            .remove(KEY_ENABLED)
            .remove(KEY_PACKAGE)
            .apply()
    }

    /**
     * The package that should be started, or `null` when there is nothing to
     * start. A stored package that [isLaunchable] rejects - uninstalled, or no
     * launchable activity left - is dropped, so a stale entry neither starts
     * anything nor keeps being shown as configured.
     */
    fun launchablePackage(isLaunchable: (String) -> Boolean): String? {
        val configured = packageName ?: return null
        if (isLaunchable(configured)) return configured

        disable()
        return null
    }

    /** Stored value, treating a blank entry as "nothing configured". */
    private val storedPackage: String?
        get() = preferences.getString(KEY_PACKAGE, null)?.trim()?.takeIf { it.isNotEmpty() }

    internal companion object {
        const val PREFERENCES_NAME = "universal_launcher_settings"
        const val KEY_ENABLED = "autostart_enabled"
        const val KEY_PACKAGE = "autostart_package"
    }
}
