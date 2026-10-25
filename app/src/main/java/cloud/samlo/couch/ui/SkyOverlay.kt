package cloud.samlo.couch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import cloud.samlo.couch.R
import cloud.samlo.couch.data.Config
import cloud.samlo.couch.data.QuickActions
import cloud.samlo.couch.data.SkyPhotos
import cloud.samlo.couch.data.Tile
import cloud.samlo.couch.data.WeatherDay
import cloud.samlo.couch.data.WeatherNow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * The sky layout's chrome, after Apple TV and LG's home: a big multicoloured clock with the date
 * and a weather card in the top left; in the top right, an inputs pill that drops down the TV's
 * sources, the Wi-Fi pill and the settings gear, over LG's control panel (sound, picture, clock,
 * TV menu, round shortcuts and a volume slider); and the photo's credit above the dock. The dock
 * itself is the rows layout's first row, drawn over [dock], a glass panel placed by the home screen.
 */
@SuppressLint("ViewConstructor")
class SkyOverlay(context: Context, private val actions: Actions) : FrameLayout(context) {

    /** What the control panel's buttons do beyond this view. */
    class Actions(
        val settings: () -> Unit,
        val reload: () -> Unit,
        val photoOnly: () -> Unit,
        val toggleClock: () -> Unit,
    )

    private val time = TextClock(context).apply {
        format12Hour = "HH:mm"
        format24Hour = "HH:mm"
        textSize = 84f
        typeface = Fonts.clock(context)
        setTextColor(Tokens.textPrimary)
        includeFontPadding = false
        setShadowLayer(context.dpf(14f), 0f, context.dpf(2f), 0x40000000)
        // The gradient spans the text, so it follows the width ("9:05" vs "21:45").
        addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) paintClock(v as TextView) }
    }
    private var palette = PALETTES.getValue("day")

    private val date = TextClock(context).apply {
        showLongDate()
        textSize = 16f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(Tokens.textPrimary)
        background = Glass.panel(context, 16f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(14), 0, context.dp(14), 0)
    }

    // The weather card: now on the left, the next five days on the right.
    private val weatherIcon = ImageView(context)
    private val weatherTemp = text(30f, "sans-serif-medium")
    private val weatherLabel = text(13f, "sans-serif").apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val weatherRange = text(12f, "sans-serif").apply { setTextColor(Glass.contentMuted.defaultColor) }
    private val days = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val weatherCard = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = Glass.panel(context, 20f)
        setPadding(context.dp(16), context.dp(10), context.dp(12), context.dp(10))
        visibility = View.GONE
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(weatherIcon, LinearLayout.LayoutParams(context.dp(34), context.dp(34)).apply { marginEnd = context.dp(6) })
                addView(weatherTemp)
            })
            addView(weatherLabel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(2) })
            addView(weatherRange)
        }, LinearLayout.LayoutParams(context.dp(132), WRAP_CONTENT))
        addView(FlatView(context).apply { background = ColorDrawable(Glass.EDGE) },
            LinearLayout.LayoutParams(context.dp(1), context.dp(64)).apply { marginEnd = context.dp(6) })
        addView(days)
    }

    // The top-right bar and its two drop-downs.
    private val inputsPill = Glass.pill(context, R.drawable.ic_input, context.getString(R.string.sky_inputs)) { toggle(inputMenu, inputsPillView()) }
        .apply { addView(Glass.icon(context, R.drawable.ic_expand, 16), LinearLayout.LayoutParams(context.dp(16), context.dp(16)).apply { marginStart = context.dp(4) }) }
    private val wifiPill = Glass.pill(context, R.drawable.ic_wifi, context.getString(R.string.quick_wifi)) { QuickActions.wifi(context) }
    private val gear = Glass.pill(context, R.drawable.ic_settings, null) { actions.settings() }.apply {
        contentDescription = context.getString(R.string.settings)
    }
    private val inputMenu = menu()
    private var inputSignature = ""

    private val pictureTile = QuickTile(context, R.drawable.ic_picture, context.getString(R.string.quick_picture), context.getString(R.string.quick_picture_sub)) {
        QuickActions.picture(context) { mode -> it.subtitle.text = mode }
    }
    private val soundTile = QuickTile(context, R.drawable.ic_volume, context.getString(R.string.quick_sound), context.getString(R.string.quick_sound_sub)) {
        QuickActions.sound(context) { mode -> it.subtitle.text = mode }
    }
    private val clockTile = QuickTile(context, R.drawable.ic_clock, context.getString(R.string.sky_clock), "") { actions.toggleClock() }
    private val volume = VolumeSlider(context)
    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(GridLayout(context).apply {
            columnCount = 2
            addView(soundTile, tileParams())
            addView(pictureTile, tileParams())
            addView(clockTile, tileParams())
            addView(QuickTile(context, R.drawable.ic_tv, context.getString(R.string.sky_tv_menu), context.getString(R.string.sky_tv_menu_sub)) {
                QuickActions.tvSettings(context)
            }, tileParams())
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(round(R.drawable.ic_eye, R.string.sky_photo_only) { actions.photoOnly() })
            addView(round(R.drawable.ic_moon, R.string.quick_screensaver) { QuickActions.screensaver(context) })
            addView(round(R.drawable.ic_refresh, R.string.sky_refresh) { actions.reload() })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
        addView(volume, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(50)).apply {
                setMargins(context.dp(5), context.dp(6), context.dp(5), context.dp(5))
        })
    }

    private var openMenu: ViewGroup? = null
    private var openAnchor: View? = null

    private val credit = text(12f, "sans-serif").apply {
        setTextColor(0xCCFFFFFF.toInt())
        gravity = Gravity.END
        maxLines = 2
        setShadowLayer(context.dpf(6f), 0f, context.dpf(1f), 0x80000000.toInt())
        visibility = View.GONE
    }

    /** The dock's glass, behind the rows; the home screen sizes and places it around the first row. */
    val dock = FlatView(context).apply { background = Glass.panel(context, 26f, 0x24FFFFFF) }

    init {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(time)
            addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(32)).apply { topMargin = context.dp(8) })
            addView(weatherCard, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(10) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(inputsPill, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(40)))
            addView(wifiPill, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(40)).apply { marginStart = context.dp(10) })
            addView(gear, LinearLayout.LayoutParams(context.dp(40), context.dp(40)).apply { marginStart = context.dp(10) })
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(bar, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(40)))
            addView(FrameLayout(context).apply {
                addView(panel, LayoutParams(context.dp(372), WRAP_CONTENT, Gravity.TOP or Gravity.END))
                // Over the panel while open.
                addView(inputMenu, LayoutParams(context.dp(220), WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { rightMargin = context.dp(100) })
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(10) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V + 4)
        })

        addView(credit, LayoutParams(context.dp(420), WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
            rightMargin = context.dp(Tokens.OVERSCAN_H)
        })

        // A drop-down closes once focus leaves it (and its pill), like a menu.
        viewTreeObserver.addOnGlobalFocusChangeListener { _, focused ->
            val menu = openMenu ?: return@addOnGlobalFocusChangeListener
            if (focused != null && focused !== openAnchor && !menu.contains(focused)) close(focusAnchor = false)
        }
    }

    /** Back closes an open drop-down; true when the key was used. */
    fun consumesKey(event: KeyEvent): Boolean {
        if (openMenu == null || event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP) close()
        return true
    }

    /** Puts the credit just above the dock's glass. */
    fun placeCredit(dockTop: Int) {
        val params = credit.layoutParams as LayoutParams
        val bottom = (height - dockTop + context.dp(8)).coerceAtLeast(0)
        if (params.bottomMargin == bottom) return
        params.bottomMargin = bottom
        credit.layoutParams = params
    }

    fun showCredit(text: String?) {
        credit.text = text
        credit.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    /** The TV's inputs, in the inputs drop-down. Rebuilt only when they change, so focus stays put. */
    fun showInputs(tiles: List<Tile>) {
        val signature = tiles.joinToString(";") { it.signature }
        if (signature == inputSignature) return
        inputSignature = signature
        inputMenu.removeAllViews()
        for (tile in tiles) {
            val pill = Glass.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) {
                close(focusAnchor = false)
                tile.launch(context)
            }
            if (tile.dimmed) pill.alpha = 0.55f
            inputMenu.addView(pill, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(40)).apply { setMargins(context.dp(5), context.dp(4), context.dp(5), context.dp(4)) })
        }
        inputsPill.visibility = if (tiles.isEmpty()) View.GONE else View.VISIBLE
    }

    fun applyLook(config: Config, timeFormat: String) {
        time.format12Hour = timeFormat
        time.format24Hour = timeFormat
        date.visibility = if (config.showDate) View.VISIBLE else View.GONE
        clockTile.subtitle.setText(if (config.clock24h) R.string.sky_clock_24 else R.string.sky_clock_12)
    }

    fun showWeather(now: WeatherNow?) {
        setPalette(SkyPhotos.timeOf(Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }, now))
        if (now == null) {
            weatherCard.visibility = View.GONE
            return
        }
        weatherIcon.setImageResource(now.icon)
        if (animating) (weatherIcon.drawable as? Animatable)?.start()
        weatherTemp.text = context.getString(R.string.sky_degrees, now.temperature)
        val label = if (now.condition.label != 0) context.getString(now.condition.label) else ""
        weatherLabel.text = listOf(label, now.place).filter { it.isNotEmpty() }.joinToString(" · ")
        weatherRange.text = context.getString(R.string.sky_range, now.high, now.low)
        days.removeAllViews()
        for (day in now.days.drop(1).take(5)) days.addView(dayColumn(day), LinearLayout.LayoutParams(context.dp(52), WRAP_CONTENT))
        days.visibility = if (days.childCount == 0) View.GONE else View.VISIBLE
        weatherCard.visibility = View.VISIBLE
    }

    private var animating = false

    fun animateWeather(on: Boolean) {
        animating = on
        (weatherIcon.drawable as? Animatable)?.let { if (on) it.start() else it.stop() }
    }

    /** Called on every return home: the network or volume may have changed while away. */
    fun refresh() {
        val wifi = QuickActions.wifiName(context)
        (wifiPill.getChildAt(wifiPill.childCount - 1) as? TextView)?.text = wifi ?: context.getString(R.string.quick_wifi_off)
        wifiPill.alpha = if (wifi == null) 0.7f else 1f
        volume.refresh()
        // The clock's colours follow the time of day, even before the next weather reload.
        showWeatherPaletteOnly()
    }

    private fun showWeatherPaletteOnly() {
        val c = Calendar.getInstance()
        setPalette(SkyPhotos.timeOf(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), null))
    }

    private fun setPalette(timeOfDay: String) {
        val next = PALETTES[timeOfDay] ?: return
        if (next === palette && time.paint.shader != null) return
        palette = next
        paintClock(time)
    }

    private fun paintClock(view: TextView) {
        val w = view.width.takeIf { it > 0 } ?: return
        view.paint.shader = LinearGradient(0f, 0f, w.toFloat(), view.height * 0.6f, palette, null, Shader.TileMode.CLAMP)
        view.invalidate()
    }

    private fun inputsPillView(): View = inputsPill

    private fun toggle(menu: LinearLayout, anchor: View) {
        if (openMenu === menu) {
            close()
            return
        }
        close(focusAnchor = false)
        if (menu.childCount == 0) return
        openMenu = menu
        openAnchor = anchor
        menu.fadeIn()
        menu.post { firstFocusable(menu)?.requestFocus() }
    }

    private fun close(focusAnchor: Boolean = true) {
        val menu = openMenu ?: return
        val anchor = openAnchor
        openMenu = null
        openAnchor = null
        menu.visibility = View.GONE
        if (focusAnchor) anchor?.requestFocus()
    }

    private fun firstFocusable(group: ViewGroup): View? {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (child.isFocusable) return child
            if (child is ViewGroup) firstFocusable(child)?.let { return it }
        }
        return null
    }

    private fun ViewGroup.contains(view: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v === this) return true
            v = v.parent as? View
        }
        return false
    }

    private fun menu() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Glass.panel(context, 24f, 0x59202428)
        setPadding(context.dp(8), context.dp(8), context.dp(8), context.dp(8))
        visibility = View.GONE
    }

    private fun dayColumn(day: WeatherDay) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(text(12f, "sans-serif-medium").apply { text = weekday(day.date) })
        addView(ImageView(context).apply { setImageResource(day.condition.icon(true)) },
            LinearLayout.LayoutParams(context.dp(28), context.dp(28)).apply { setMargins(0, context.dp(2), 0, context.dp(2)) })
        addView(text(12f, "sans-serif-medium").apply { text = context.getString(R.string.sky_degrees, day.high) })
        addView(text(11f, "sans-serif").apply {
            text = context.getString(R.string.sky_degrees, day.low)
            setTextColor(Glass.contentMuted.defaultColor)
        })
    }

    private fun weekday(iso: String): String = try {
        SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso)!!)
    } catch (e: Exception) {
        ""
    }

    private fun text(size: Float, family: String) = TextView(context).apply {
        textSize = size
        typeface = Typeface.create(family, Typeface.NORMAL)
        setTextColor(Tokens.textPrimary)
        includeFontPadding = false
    }

    private fun round(icon: Int, label: Int, onClick: () -> Unit) = Glass.pill(context, icon, null) { onClick() }.apply {
        contentDescription = context.getString(label)
        layoutParams = LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { setMargins(context.dp(10), context.dp(6), context.dp(10), context.dp(2)) }
        background = Glass.focusable(context, 24f)
    }

    private fun tileParams() = GridLayout.LayoutParams().apply {
        width = context.dp(176)
        height = context.dp(54)
        setMargins(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
    }

    /** A quick-settings tile: icon, title and a muted second line, as in Apple TV's control centre. */
    private class QuickTile(context: Context, icon: Int, title: String, sub: String, onClick: (QuickTile) -> Unit) : LinearLayout(context) {
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
            setOnClickListener { onClick(this) }
        }
    }

    /**
     * LG's backlight slider, as a volume slider: focus it, then left and right step the TV's
     * volume like the remote does. The fill shows the level; the number is a percentage.
     */
    private class VolumeSlider(context: Context) : FrameLayout(context) {
        private val fill = FlatView(context).apply {
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), rounded(context, 0x2615171A))
                addState(intArrayOf(), rounded(context, 0x33FFFFFF))
            }
            isDuplicateParentStateEnabled = true
        }
        private val value = TextView(context).apply {
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Glass.content)
            isDuplicateParentStateEnabled = true
        }
        private var level = 0

        init {
            isFocusable = true
            isClickable = true
            background = Glass.focusable(context, 25f)
            contentDescription = context.getString(R.string.sky_volume)
            addView(fill, LayoutParams(0, MATCH_PARENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isDuplicateParentStateEnabled = true
                setPadding(context.dp(16), 0, context.dp(18), 0)
                addView(Glass.icon(context, R.drawable.ic_volume, 22), LinearLayout.LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(12) })
                addView(TextView(context).apply {
                    setText(R.string.sky_volume)
                    textSize = 14f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(Glass.content)
                    isDuplicateParentStateEnabled = true
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(value)
            }, fill())
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) placeFill() }
        }

        fun refresh() = show(QuickActions.volume(context))

        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                show(QuickActions.adjustVolume(context, keyCode == KeyEvent.KEYCODE_DPAD_RIGHT))
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }

        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || super.onKeyUp(keyCode, event)

        private fun show(percent: Int) {
            level = percent.coerceIn(0, 100)
            value.text = level.toString()
            placeFill()
        }

        private fun rounded(context: Context, color: Int) = GradientDrawable().apply {
            cornerRadius = context.dpf(25f)
            setColor(color)
        }

        private fun placeFill() {
            val params = fill.layoutParams
            val w = width * level / 100
            if (params.width == w) return
            params.width = w
            fill.layoutParams = params
        }
    }

    private companion object {
        /** The clock's colours for each time of day, left to right. */
        val PALETTES = mapOf(
            "dawn" to intArrayOf(0xFFFFC3A0.toInt(), 0xFFFF8FB1.toInt(), 0xFFC3A6FF.toInt()),
            "day" to intArrayOf(0xFFFFFFFF.toInt(), 0xFF9BE7FF.toInt(), 0xFFB9F6CA.toInt()),
            "dusk" to intArrayOf(0xFFFFE082.toInt(), 0xFFFF9E80.toInt(), 0xFFF48FB1.toInt()),
            "night" to intArrayOf(0xFFB3E5FC.toInt(), 0xFFC5B3FF.toInt(), 0xFFF8BBD0.toInt()),
        )
    }
}
