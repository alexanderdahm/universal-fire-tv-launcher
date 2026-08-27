package com.example.universallauncher

import android.content.pm.PackageManager

/**
 * Collects every application on the device that can be started.
 *
 * The scan runs over the same categories as [AppLauncher], TV entries first, so
 * an app that ships both a leanback and a phone activity is represented by its
 * TV entry point. Loading labels and icons touches the disk - call this off the
 * main thread.
 */
class InstalledAppsRepository(private val packageManager: PackageManager) {

    /**
     * All launchable apps, sorted by label, excluding [excludedPackage]
     * (this launcher itself).
     */
    fun launchableApps(excludedPackage: String): List<LaunchableApp> {
        val byPackage = LinkedHashMap<String, LaunchableApp>()

        LAUNCHER_CATEGORIES.forEach { category ->
            packageManager.queryLauncherActivities(launcherQueryIntent(category))
                .filter { it.activityInfo.packageName != excludedPackage }
                .forEach { resolved ->
                    val activityInfo = resolved.activityInfo
                    // getOrPut keeps the first hit, so LEANBACK_LAUNCHER wins
                    // over LAUNCHER for apps that expose both.
                    byPackage.getOrPut(activityInfo.packageName) {
                        LaunchableApp(
                            packageName = activityInfo.packageName,
                            label = resolved.loadLabel(packageManager).toString(),
                            icon = runCatching { resolved.loadIcon(packageManager) }.getOrNull(),
                            launchIntent = explicitLaunchIntent(category, activityInfo)
                        )
                    }
                }
        }

        return byPackage.values.sortedBy { it.label.lowercase() }
    }
}
