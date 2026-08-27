package com.example.universallauncher

import android.content.Intent
import android.graphics.drawable.Drawable

/**
 * One startable application on this device, as shown in the picker.
 *
 * @param packageName package the entry belongs to
 * @param label user visible name
 * @param icon icon loaded from the owning package
 * @param launchIntent explicit intent that starts this entry
 */
data class LaunchableApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val launchIntent: Intent
)
