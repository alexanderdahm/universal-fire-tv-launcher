package com.example.universallauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Resolves and starts other applications.
 *
 * Resolution order for a package:
 *
 * 1. [PackageManager.getLaunchIntentForPackage] - the cheap, standard way.
 * 2. Its launcher activities, `LEANBACK_LAUNCHER` first, `LAUNCHER` second.
 * 3. The first exported match, started as an explicit component.
 */
object AppLauncher {

    private const val TAG = "UniversalLauncher"

    /** Intent that starts [packageName], or `null` when nothing can start it. */
    fun launchIntentFor(packageManager: PackageManager, packageName: String): Intent? =
        packageManager.getLaunchIntentForPackage(packageName)
            ?: firstLauncherActivityIntent(packageManager, packageName)

    /**
     * Starts [intent] the way a home screen does: in its own task.
     * Returns `false` when the target could not be started.
     */
    fun start(context: Context, intent: Intent): Boolean {
        val launchIntent = Intent(intent)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(launchIntent)
            Log.i(TAG, "Started ${launchIntent.component ?: launchIntent.`package`}")
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Launch intent could not be started", e)
            false
        } catch (e: SecurityException) {
            // The activity exists but is not exported to us.
            Log.e(TAG, "Not allowed to start the target activity", e)
            false
        }
    }

    /** Convenience for the common "resolve, then start" case. */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = launchIntentFor(context.packageManager, packageName)
        if (intent == null) {
            Log.w(TAG, "No launchable activity found for $packageName")
            return false
        }
        return start(context, intent)
    }

    private fun firstLauncherActivityIntent(
        packageManager: PackageManager,
        packageName: String
    ): Intent? {
        LAUNCHER_CATEGORIES.forEach { category ->
            val activity = packageManager
                .queryLauncherActivities(launcherQueryIntent(category, packageName))
                .firstOrNull()
            if (activity != null) {
                return explicitLaunchIntent(category, activity.activityInfo)
            }
        }
        return null
    }
}
