package com.example.universallauncher

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
 * [AppLauncher.start] path the single app mode uses.
 */
class AppPickerFragment : VerticalGridSupportFragment() {

    private val appsAdapter = ArrayObjectAdapter(AppCardPresenter())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        title = getString(R.string.app_name)
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

    private companion object {
        const val COLUMNS = 5
    }
}
