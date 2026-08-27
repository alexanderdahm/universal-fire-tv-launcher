package com.example.universallauncher

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build

/**
 * Shared launcher plumbing: the categories a startable activity can live under,
 * the intents used to find those activities, and the `PackageManager`
 * compatibility shims. Both the direct launch path and the app picker build on
 * this file, so the resolution rules exist exactly once.
 */

/**
 * Categories that mark an activity as startable, most TV specific first.
 *
 * Fire TV and Android TV builds of an app frequently export *only* a
 * `LEANBACK_LAUNCHER` activity, which older `getLaunchIntentForPackage()`
 * implementations do not find - hence the explicit fallback.
 */
internal val LAUNCHER_CATEGORIES: List<String> = listOf(
    Intent.CATEGORY_LEANBACK_LAUNCHER,
    Intent.CATEGORY_LAUNCHER
)

/**
 * Query intent for launchable activities in [category], optionally narrowed
 * down to a single [packageName].
 */
internal fun launcherQueryIntent(category: String, packageName: String? = null): Intent =
    Intent(Intent.ACTION_MAIN).apply {
        addCategory(category)
        packageName?.let { setPackage(it) }
    }

/** Explicit intent that starts exactly [activityInfo]. */
internal fun explicitLaunchIntent(category: String, activityInfo: ActivityInfo): Intent =
    Intent(Intent.ACTION_MAIN).apply {
        addCategory(category)
        setClassName(activityInfo.packageName, activityInfo.name)
    }

/** Launchable activities for [intent], newest API where available. */
internal fun PackageManager.queryLauncherActivities(intent: Intent): List<ResolveInfo> =
    queryIntentActivitiesCompat(intent).filter { it.activityInfo != null && it.activityInfo.exported }

/**
 * The int-flag overload is deprecated on API 33+, but it is the only one
 * available on Fire OS 5 (API 22), hence the explicit version split.
 */
@Suppress("DEPRECATION")
private fun PackageManager.queryIntentActivitiesCompat(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
    } else {
        queryIntentActivities(intent, 0)
    }
