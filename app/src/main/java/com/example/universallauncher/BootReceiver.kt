package com.example.universallauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Starts the configured autostart app once the Fire TV has finished booting.
 *
 * It deliberately does *not* open this launcher: it resolves the package stored
 * by [AutostartSettings] and hands it to [AppLauncher], the very same launch
 * path the picker and the single app mode use.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Only ever react to the boot broadcast, whatever else may arrive here.
        if (!isBootCompletedAction(intent.action)) return

        // BOOT_COMPLETED can be delivered more than once (and the receiver may
        // be re-registered on a warm process), autostart must happen once.
        if (!bootGuard.tryAcquire()) {
            Log.i(TAG, "BOOT_COMPLETED received again, autostart already handled")
            return
        }

        val appContext = context.applicationContext
        val packageName = autostartPackage(
            settings = AutostartSettings(appContext),
            buildTimePackage = LauncherConfig.bootAutostartPackage
        ) { candidate ->
            AppLauncher.launchIntentFor(appContext.packageManager, candidate) != null
        }

        if (packageName == null) {
            Log.i(TAG, "No autostart app configured, or the configured one is gone")
            return
        }

        // goAsync() keeps the process alive across the delay below; a broadcast
        // receiver may run for roughly ten seconds, which the delay stays well
        // inside of.
        val pendingResult = goAsync()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                runCatching {
                    if (!AppLauncher.launch(appContext, packageName)) {
                        Log.w(TAG, "Autostart of $packageName failed")
                    }
                }.onFailure { Log.e(TAG, "Autostart of $packageName threw", it) }

                pendingResult.finish()
            },
            AUTOSTART_DELAY_MS
        )
    }

    internal companion object {

        private const val TAG = "UniversalLauncher"

        /**
         * Fire TV keeps starting system components and its home screen for a
         * moment after BOOT_COMPLETED; an app started in the very same instant
         * risks being pushed straight back behind the home screen. Three
         * seconds lands after that settling phase, is short enough not to feel
         * like a hang, and stays well inside the time budget a broadcast
         * receiver has with `goAsync()`.
         */
        const val AUTOSTART_DELAY_MS = 3_000L

        private val bootGuard = OneShotGuard()
    }
}

/**
 * The package to start after a boot, or `null` when there is nothing to start.
 *
 * The app the user picked wins; [buildTimePackage] is the fallback for single
 * app builds assembled with `-Pautostart=true`, which have no picker to choose
 * from. Either way the package has to pass [isLaunchable].
 */
internal fun autostartPackage(
    settings: AutostartSettings,
    buildTimePackage: String?,
    isLaunchable: (String) -> Boolean
): String? = settings.launchablePackage(isLaunchable)
    ?: buildTimePackage?.takeIf(isLaunchable)

/** `true` only for the boot broadcast this receiver is registered for. */
internal fun isBootCompletedAction(action: String?): Boolean =
    action == Intent.ACTION_BOOT_COMPLETED

/** Lets the first caller through and nobody after it. */
internal class OneShotGuard {

    private val used = AtomicBoolean(false)

    fun tryAcquire(): Boolean = used.compareAndSet(false, true)
}
