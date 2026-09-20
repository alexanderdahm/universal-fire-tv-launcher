package com.example.universallauncher

/**
 * Typed view onto the build time configuration.
 *
 * Every value originates from the `LAUNCHER CONFIGURATION` block in
 * `app/build.gradle.kts`; nothing in the code below ever hard codes a package
 * name, a label or a colour.
 */
object LauncherConfig {

    /** Package this build launches, or an empty string for the app picker. */
    val targetPackage: String = BuildConfig.TARGET_PACKAGE.trim()

    /** Human readable name of this launcher, shown on the tile and in dialogs. */
    val appLabel: String = BuildConfig.APP_LABEL

    /** Accent colour (`#AARRGGBB`) the artwork and the picker are built from. */
    val accentColor: String = BuildConfig.APP_ICON_COLOR

    /**
     * `true` when this build starts one specific app, `false` when it should
     * offer the list of every launchable application on the device.
     */
    val isSingleAppLauncher: Boolean = targetPackage.isNotEmpty()

    /**
     * Package this build starts after a boot on its own, or `null`.
     *
     * A single app build has no picker to configure autostart from, so the
     * choice is made at build time with `-Pautostart=true`. A picker build has
     * no target package and always keeps the user's on device choice instead.
     */
    val bootAutostartPackage: String? =
        targetPackage.takeIf { BuildConfig.AUTOSTART_TARGET && isSingleAppLauncher }
}
