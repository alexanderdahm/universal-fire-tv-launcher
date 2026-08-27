package com.example.universallauncher

import android.os.Bundle
import androidx.fragment.app.FragmentActivity

/**
 * Host of the Leanback grid shown when no target package is configured.
 *
 * It is a plain [FragmentActivity] because the Leanback fragments require a
 * `Theme.Leanback` derived theme, which is not an AppCompat theme.
 */
class AppPickerActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, AppPickerFragment())
                .commit()
        }
    }
}
