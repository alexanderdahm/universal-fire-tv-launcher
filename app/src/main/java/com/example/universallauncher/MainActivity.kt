package com.example.universallauncher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Invisible entry point.
 *
 * The activity has no layout: it either starts the configured application and
 * finishes, or - when no target package is configured - hands over to the
 * [AppPickerActivity] which lists every launchable app on the device.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No setContentView() on purpose - this activity never renders anything.

        if (LauncherConfig.isSingleAppLauncher) {
            launchConfiguredApp()
        } else {
            showAppPicker()
        }
    }

    /** Single app mode: resolve the configured package, start it, disappear. */
    private fun launchConfiguredApp() {
        if (AppLauncher.launch(this, LauncherConfig.targetPackage)) {
            finish()
        } else {
            showCannotLaunchDialog()
        }
    }

    /** Picker mode: show the list of installed launchable applications. */
    private fun showAppPicker() {
        startActivity(Intent(this, AppPickerActivity::class.java))
        finish()
    }

    /**
     * The only dialog this app can produce. The activity is themed translucent,
     * so nothing but the dialog is visible.
     */
    private fun showCannotLaunchDialog() {
        if (isFinishing) return

        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage(getString(R.string.message_cannot_launch, LauncherConfig.appLabel))
            .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
            .setOnDismissListener { finish() }
            .setCancelable(true)
            .show()
    }
}
