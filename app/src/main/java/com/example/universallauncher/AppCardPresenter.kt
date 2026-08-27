package com.example.universallauncher

import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter

/**
 * Renders one [LaunchableApp] as a Leanback card: the app icon on a neutral
 * plate, its name on an info strip tinted with the configured accent colour.
 */
class AppCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        val resources = context.resources

        val cardView = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(
                resources.getDimensionPixelSize(R.dimen.app_card_width),
                resources.getDimensionPixelSize(R.dimen.app_card_height)
            )
            setInfoAreaBackgroundColor(ContextCompat.getColor(context, R.color.launcher_icon_color))
            setBackgroundColor(ContextCompat.getColor(context, R.color.picker_card_background))

            mainImageView?.apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                val padding = resources.getDimensionPixelSize(R.dimen.app_card_icon_padding)
                setPadding(padding, padding, padding, padding)
            }
        }

        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val app = item as? LaunchableApp ?: return
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = app.label
        cardView.contentText = app.packageName
        cardView.mainImage = app.icon
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // Release the icon so the grid does not hold on to every app's drawable.
        (viewHolder.view as ImageCardView).mainImage = null
    }
}
