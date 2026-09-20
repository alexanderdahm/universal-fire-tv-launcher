package com.example.universallauncher

import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.leanback.app.VerticalGridSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.FocusHighlight
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.VerticalGridPresenter
import java.util.concurrent.Executors

/**
 * D-pad friendly grid of every launchable application on the device.
 *
 * Selecting a card starts that app immediately, through the same
 * [AppLauncher.start] path the single app mode uses. Holding OK on a card
 * offers to make it the autostart app - see [AutostartSettings].
 */
class AppPickerFragment : VerticalGridSupportFragment() {

    private val autostartSettings by lazy { AutostartSettings(requireContext()) }
    private val appsAdapter = ArrayObjectAdapter(
        AppCardPresenter(
            isAutostartApp = { packageName -> autostartSettings.isAutostartApp(packageName) },
            onLongClicked = { app -> showAutostartDialog(app) }
        )
    )
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The apps currently in the grid, used to label the configured package. */
    private var loadedApps: List<LaunchableApp> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateTitle()
        gridPresenter = VerticalGridPresenter(FocusHighlight.ZOOM_FACTOR_MEDIUM).apply {
            numberOfColumns = COLUMNS
        }
        adapter = appsAdapter

        onItemViewClickedListener = OnItemViewClickedListener { _, item, _, _ ->
            (item as? LaunchableApp)?.let(::launch)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadInstalledApps()
    }

    override fun onDestroy() {
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    /** Scanning packages and loading their icons is slow - keep it off the UI thread. */
    private fun loadInstalledApps() {
        val packageManager: PackageManager = requireContext().packageManager
        val ownPackage = requireContext().packageName

        backgroundExecutor.execute {
            val apps = InstalledAppsRepository(packageManager).launchableApps(ownPackage)

            mainHandler.post {
                if (!isAdded) return@post

                loadedApps = apps
                // An autostart app that has been uninstalled since it was picked
                // drops out here, so the title never claims a package that is gone.
                autostartSettings.launchablePackage { configured ->
                    apps.any { it.packageName == configured }
                }
                updateTitle()

                appsAdapter.clear()
                appsAdapter.addAll(0, apps)

                if (apps.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.message_no_apps_found, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launch(app: LaunchableApp) {
        if (!AppLauncher.start(requireContext(), app.launchIntent)) {
            Toast.makeText(
                requireContext(),
                getString(R.string.message_cannot_launch, app.label),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * One confirmation step for the autostart setting: it states what is
     * configured right now and offers the single action that fits [app] -
     * setting it, or switching autostart off again when it is already the one.
     *
     * A platform dialog, not an AppCompat one: the picker runs under
     * `Theme.Leanback`, which is not an AppCompat theme. Its buttons are
     * D-pad focusable, so this stays remote only.
     */
    private fun showAutostartDialog(app: LaunchableApp) {
        if (!isAdded) return

        val isCurrent = autostartSettings.isAutostartApp(app.packageName)
        val action = if (isCurrent) R.string.action_disable_autostart else R.string.action_set_autostart

        AlertDialog.Builder(requireContext())
            .setTitle(app.label)
            .setMessage(autostartStatusText())
            .setPositiveButton(action) { _, _ ->
                if (isCurrent) disableAutostart() else setAutostartApp(app)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** "Nothing configured", or the app that is going to start after a boot. */
    private fun autostartStatusText(): String {
        val configured = autostartSettings.packageName ?: return getString(R.string.autostart_none)
        return getString(R.string.autostart_current, labelFor(configured))
    }

    private fun setAutostartApp(app: LaunchableApp) {
        autostartSettings.setAutostartApp(app.packageName)
        onAutostartChanged()
        Toast.makeText(
            requireContext(),
            getString(R.string.message_autostart_set, app.label),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun disableAutostart() {
        autostartSettings.disable()
        onAutostartChanged()
        Toast.makeText(requireContext(), R.string.message_autostart_disabled, Toast.LENGTH_LONG).show()
    }

    /** Title and the badge on the cards both follow the setting. */
    private fun onAutostartChanged() {
        updateTitle()
        appsAdapter.notifyArrayItemRangeChanged(0, appsAdapter.size())
    }

    private fun updateTitle() {
        val appName = getString(R.string.app_name)
        val configured = autostartSettings.packageName
        title = if (configured == null) {
            getString(R.string.title_autostart_hint, appName)
        } else {
            getString(R.string.title_autostart_active, appName, labelFor(configured))
        }
    }

    /** Label of [packageName], falling back to the package itself. */
    private fun labelFor(packageName: String): String =
        loadedApps.firstOrNull { it.packageName == packageName }?.label ?: packageName

    private companion object {
        const val COLUMNS = 5
    }
}
