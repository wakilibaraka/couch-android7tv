package cloud.samlo.couch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import cloud.samlo.couch.data.Images
import cloud.samlo.couch.data.Tile

/**
 * The top of the screen: the focused item's art behind a scrim, with its title, meta line and
 * description. Text updates on every focus move (fixed-size views, so no layout pass); the art
 * waits until focus settles, then crossfades between two stacked image views.
 */
@SuppressLint("ViewConstructor")
class Hero(context: Context, private val images: Images) : FrameLayout(context) {
    private val main = Handler(Looper.getMainLooper())
    private val below = backdropView()
    private val above = backdropView().apply { alpha = 0f }
    private val title = text(34f, Tokens.textPrimary, "sans-serif-medium")
    private val meta = text(15f, Tokens.textMuted, "sans-serif")
    private val description = text(15f, 0xCCEDEDEF.toInt(), "sans-serif").apply {
        maxLines = 3
        setLineSpacing(0f, 1.15f)
    }
    private val scrim = FlatView(context).apply { background = scrimDrawable("medium") }
    private val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var sky = false
    private var dim = "medium"

    /** Shown behind apps, inputs, and anything without art of its own. */
    var wallpaperUrl: String? = null
        set(value) {
            field = value
            if (currentTile?.backdropUrl == null) requestBackdrop(value)
        }

    @Volatile
    private var pendingUrl: String? = null
    private var shownUrl: String? = null
    private var currentTile: Tile? = null
    private val settle = Runnable { requestBackdrop(currentTile?.backdropUrl ?: wallpaperUrl) }

    init {
        addView(below, fill())
        addView(above, fill())
        addView(scrim, fill())

        column.addView(title, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(46)))
        column.addView(meta, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(24)))
        column.addView(description, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, context.dp(70)).apply {
            topMargin = context.dp(8)
        })
        addView(column, LayoutParams(context.dp(500), LayoutParams.WRAP_CONTENT).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V + 56)
        })
    }

    fun show(tile: Tile) {
        currentTile = tile
        title.text = tile.label
        meta.text = tile.meta.orEmpty()
        description.text = tile.description.orEmpty()
        main.removeCallbacks(settle)
        main.postDelayed(settle, Tokens.HERO_SETTLE_MS)
    }

    /** Instructions in place of the title while a mode (like reordering) is active. */
    fun showHint(heading: String, hint: String) {
        title.text = heading
        meta.text = hint
        description.text = ""
    }

    /** How much the art is darkened behind the text: "light", "medium" or "strong". */
    fun setDim(level: String) {
        dim = level
        scrim.background = if (sky) skyScrimDrawable(level) else scrimDrawable(level)
    }

    /** The sky layout shows the photo almost bare, with no title or description over it. */
    fun setSky(on: Boolean) {
        sky = on
        column.visibility = if (on) View.GONE else View.VISIBLE
        setDim(dim)
    }

    fun setShowDescription(show: Boolean) {
        description.visibility = if (show) View.VISIBLE else View.INVISIBLE
    }

    private fun requestBackdrop(url: String?) {
        if (url == shownUrl) return
        pendingUrl = url
        if (url == null) {
            crossfade(null)
            return
        }
        // 720p in RGB_565: the scrim hides the softness, and it moves less than half the bytes of 1080p ARGB.
        images.load(url, context.dp(640), context.dp(360), 0f, opaque = true, isCurrent = { pendingUrl == url }) {
            shownUrl = url
            crossfade(it)
        }
    }

    private fun crossfade(bitmap: Bitmap?) {
        if (bitmap == null) shownUrl = null
        above.animate().cancel()
        if (above.alpha > 0.5f) {
            // The top view is showing: put the new art underneath and fade the top away.
            below.setImageBitmap(bitmap)
            above.animate().alpha(0f).setDuration(Tokens.HERO_FADE_MS).setInterpolator(Tokens.ease).start()
        } else {
            above.setImageBitmap(bitmap)
            above.animate().alpha(1f).setDuration(Tokens.HERO_FADE_MS).setInterpolator(Tokens.ease).start()
        }
    }

    private fun backdropView() = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }

    private fun text(size: Float, color: Int, family: String) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        typeface = Typeface.create(family, Typeface.NORMAL)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }
}
