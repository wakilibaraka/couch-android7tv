package cloud.samlo.couch.home

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.index.IndexScreen
import cloud.samlo.couch.ui.Ambient
import cloud.samlo.couch.ui.Card
import cloud.samlo.couch.ui.Hero
import cloud.samlo.couch.ui.Rows
import cloud.samlo.couch.ui.SkyOverlay
import cloud.samlo.couch.ui.Tokens
import cloud.samlo.couch.ui.dp

/**
 * The home screen's view tree, bottom to top: the rows layout (hero, the sky layout's dock glass,
 * rows, top bar, the sky layout's clock and pills), the Index layout over it, and the ambient
 * overlay. Only the layout the user chose is visible; sky is the rows layout restyled.
 */
class HomeViews(
    activity: Activity,
    images: Images,
    onSettings: () -> Unit,
    onUpdate: () -> Unit,
    skyActions: SkyOverlay.Actions,
    onAppMenu: (Tile) -> Unit,
    onProgramMenu: (Card) -> Unit,
) {
    val hero = Hero(activity, images)
    val rows = Rows(activity)
    val topBar = TopBar(activity, onSettings, onUpdate)
    val index = IndexScreen(activity, images, onSettings, onUpdate, onAppMenu, onProgramMenu).apply { visibility = View.GONE }
    val ambient = Ambient(activity, images)
    val sky = SkyOverlay(activity, skyActions).apply { visibility = View.GONE }
    private val window = activity.window

    val root = FrameLayout(activity).apply {
        addView(hero, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(sky.dock, FrameLayout.LayoutParams(MATCH_PARENT, 0).apply {
            leftMargin = activity.dp(Tokens.OVERSCAN_H - 18)
            rightMargin = activity.dp(Tokens.OVERSCAN_H - 18)
        })
        addView(rows, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
            topMargin = activity.dp(Tokens.ROWS_TOP)
        })
        addView(topBar, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            topMargin = activity.dp(Tokens.OVERSCAN_V)
            marginEnd = activity.dp(Tokens.OVERSCAN_H)
        })
        addView(sky, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(index, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        addView(ambient, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
    }

    /** True when the rows layout is styled as the sky layout. */
    var skyShown = false
        private set

    init {
        // The dock's glass follows the first row, whose height depends on the card size.
        // Posted: placing it changes layout params, which must not happen mid-layout.
        val place = Runnable { if (skyShown) placeDock() }
        rows.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post(place) }
        rows.column.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> v.post(place) }
    }

    /** Switches the rows layout between its own look and the sky layout's. */
    fun showSky(show: Boolean) {
        if (show == skyShown) return
        skyShown = show
        sky.visibility = if (show) View.VISIBLE else View.GONE
        sky.dock.visibility = if (show) View.VISIBLE else View.GONE
        topBar.visibility = if (show) View.GONE else View.VISIBLE
        hero.setSky(show)
        (rows.layoutParams as FrameLayout.LayoutParams).topMargin =
            rows.context.dp(if (show) Tokens.SKY_ROWS_TOP else Tokens.ROWS_TOP)
        rows.requestLayout()
        setSectionTitles(!show)
    }

    /** Sky has no row titles: the dock speaks for itself, as on Apple TV. */
    fun setSectionTitles(shown: Boolean) {
        for (section in rows.sections()) section.title.visibility = if (shown) View.VISIBLE else View.GONE
    }

    private fun placeDock() {
        val first = rows.sections().firstOrNull() ?: return
        val pad = rows.context.dp(6)
        val top = rows.top + first.top + first.row.top - pad
        val height = first.row.height + pad * 2
        val params = sky.dock.layoutParams as FrameLayout.LayoutParams
        if (params.topMargin == top && params.height == height) return
        params.topMargin = top
        params.height = height
        sky.dock.layoutParams = params
        sky.placeCredit(rows.top + first.top + first.row.top - pad)
    }

    /** True when the Index layout is the one showing. */
    val indexShown: Boolean get() = index.visibility == View.VISIBLE

    /** Shows one layout and hides the other, so the hidden one costs nothing to draw. */
    fun showIndex(show: Boolean) {
        if (show == indexShown) return
        // Index sits on a flat colour; painting it as the window background avoids a full-screen layer.
        if (show) window.setBackgroundDrawable(ColorDrawable(Tokens.background)) else window.setBackgroundDrawableResource(R.drawable.background)
        index.visibility = if (show) View.VISIBLE else View.GONE
        val rowsVisibility = if (show) View.GONE else View.VISIBLE
        hero.visibility = rowsVisibility
        rows.visibility = rowsVisibility
        topBar.visibility = if (skyShown) View.GONE else rowsVisibility
        sky.visibility = if (skyShown) rowsVisibility else View.GONE
        sky.dock.visibility = sky.visibility
    }
}
