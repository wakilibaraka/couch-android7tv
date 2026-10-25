package cloud.samlo.couch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.QuickActions
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.data.WeatherNow

/**
 * The sky layout's chrome, after Apple TV: a big clock with the date and weather in the top left;
 * the TV's inputs as glass pills and the settings gear in the top right, with quick-settings
 * tiles under them; and the photo's credit above the dock. The dock itself is the rows layout's
 * first row, drawn over [dock], a glass panel placed by the home screen.
 */
@SuppressLint("ViewConstructor")
class SkyOverlay(context: Context, onSettings: () -> Unit) : FrameLayout(context) {
    private val time = TextClock(context).apply {
        format12Hour = "HH:mm"
        format24Hour = "HH:mm"
        textSize = 78f
        letterSpacing = -0.02f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Tokens.textPrimary)
        includeFontPadding = false
        setShadowLayer(context.dpf(14f), 0f, context.dpf(2f), 0x40000000)
    }
    private val date = TextClock(context).apply {
        showLongDate()
        textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Tokens.textPrimary)
        background = Glass.panel(context, 16f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(14), 0, context.dp(14), 0)
    }
    private val weatherIcon = ImageView(context)
    private val weatherText = TextView(context).apply {
        textSize = 16f
        setTextColor(Tokens.textPrimary)
        setShadowLayer(context.dpf(8f), 0f, context.dpf(1f), 0x59000000)
    }
    private val weatherRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(weatherIcon, LinearLayout.LayoutParams(context.dp(26), context.dp(26)).apply { marginEnd = context.dp(8) })
        addView(weatherText)
    }

    private val pills = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
    }
    private val inputPills = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val inputScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(inputPills)
    }
    private var inputSignature = ""

    private val wifiTile: QuickTile
    private val pictureTile: QuickTile
    private val soundTile: QuickTile
    private val credit = TextView(context).apply {
        visibility = View.GONE
    }

    /** The dock's glass, behind the rows; the home screen sizes and places it around the first row. */
    val dock = FlatView(context).apply { background = Glass.panel(context, 26f, 0x24FFFFFF) }

    init {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(time)
            addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(32)).apply { topMargin = context.dp(10) })
            addView(weatherRow, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = context.dp(10)
                marginStart = context.dp(2)
            })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        pills.addView(inputScroll, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(40)))
        pills.addView(
            Glass.pill(context, R.drawable.ic_settings, null) { onSettings() }.apply {
                contentDescription = context.getString(R.string.settings)
            },
            LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginStart = context.dp(10) },
        )

        wifiTile = QuickTile(context, R.drawable.ic_wifi, context.getString(R.string.quick_wifi)) { QuickActions.wifi(context) }
        pictureTile = QuickTile(context, R.drawable.ic_picture, context.getString(R.string.quick_picture), context.getString(R.string.quick_picture_sub)) {
            QuickActions.picture(context) { mode -> pictureTile.subtitle.text = mode }
        }
        soundTile = QuickTile(context, R.drawable.ic_volume, context.getString(R.string.quick_sound), context.getString(R.string.quick_sound_sub)) {
            QuickActions.sound(context) { mode -> soundTile.subtitle.text = mode }
        }
        val grid = GridLayout(context).apply {
            columnCount = 2
            addView(wifiTile, tileParams())
            addView(pictureTile, tileParams())
            addView(soundTile, tileParams())
            addView(QuickTile(context, R.drawable.ic_moon, context.getString(R.string.quick_screensaver), context.getString(R.string.quick_screensaver_sub)) { QuickActions.screensaver(context) }, tileParams())
        }

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(pills, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(40)))
            addView(grid, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(14) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V + 4)
            leftMargin = context.dp(340)
        })
    }

    /** Puts the credit just above the dock's glass. */
    fun placeCredit(dockTop: Int) {
        // Disabled: photo descriptions are hidden
    }

    /** The TV's inputs, left of the gear. Rebuilt only when they change, so focus stays put. */
    fun showInputs(tiles: List<Tile>) {
        val signature = tiles.joinToString(";") { it.signature }
        if (signature == inputSignature) return
        inputSignature = signature
        inputPills.removeAllViews()
        for (tile in tiles) {
            val pill = Glass.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) { tile.launch(context) }
            if (tile.dimmed) pill.alpha = 0.55f
            inputPills.addView(pill, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(40)).apply {
                if (inputPills.childCount > 0) marginStart = context.dp(10)
            })
        }
    }

    fun applyLook(config: Config, timeFormat: String) {
        time.format12Hour = timeFormat
        time.format24Hour = timeFormat
        date.visibility = if (config.showDate) View.VISIBLE else View.GONE
    }

    fun showWeather(now: WeatherNow?) {
        if (now == null) {
            weatherRow.visibility = View.GONE
            return
        }
        weatherIcon.setImageResource(now.icon)
        if (animating) (weatherIcon.drawable as? Animatable)?.start()
        val label = if (now.condition.label != 0) context.getString(now.condition.label) else ""
        weatherText.text = context.getString(R.string.weather_line, now.temperature, label, now.place)
        weatherRow.visibility = View.VISIBLE
    }

    private var animating = false

    fun animateWeather(on: Boolean) {
        animating = on
        (weatherIcon.drawable as? Animatable)?.let { if (on) it.start() else it.stop() }
    }

    fun showCredit(text: String?) {
        credit.visibility = View.GONE
    }

    /** Called on every return home: the network may have changed while away. */
    fun refresh() {
        wifiTile.subtitle.text = QuickActions.wifiName(context) ?: context.getString(R.string.quick_wifi_off)
    }

    private fun tileParams() = GridLayout.LayoutParams().apply {
        width = context.dp(176)
        height = context.dp(54)
        setMargins(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
    }

    /** A quick-settings tile: icon, title and a muted second line, as in Apple TV's control centre. */
    private class QuickTile(context: Context, icon: Int, title: String, sub: String = "", onClick: () -> Unit) : LinearLayout(context) {
        val subtitle = TextView(context).apply {
            text = sub
            textSize = 12f
            setTextColor(Glass.contentMuted)
            isDuplicateParentStateEnabled = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            background = Glass.focusable(context, 27f)
            setPadding(context.dp(16), 0, context.dp(14), 0)
            addView(Glass.icon(context, icon, 22), LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(12) })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                isDuplicateParentStateEnabled = true
                addView(TextView(context).apply {
                    text = title
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(Glass.content)
                    isDuplicateParentStateEnabled = true
                    maxLines = 1
                })
                addView(subtitle)
            })
            setOnClickListener { onClick() }
        }
    }
}
