package cloud.samlo.couch.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.Animatable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
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
import cloud.samlo.couch.data.WeatherHour
import cloud.samlo.couch.data.WeatherNow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The sky layout's chrome, after LG's home: a big clock with one colour per digit (the colours
 * follow the time of day), the date, the weather line and a glass forecast card on the left; an
 * inputs pill that drops down the TV's sources and the settings gear in the top right, over the
 * control panel (sound, picture, photo theme, screensaver, round shortcuts, a volume slider). No
 * text sits on the photo itself. The dock is the rows layout's first row, drawn over [dock], a
 * glass panel placed by the home screen.
 */
@SuppressLint("ViewConstructor")
class SkyOverlay(context: Context, private val actions: Actions) : FrameLayout(context) {

    /** What the panel's buttons do beyond this view. */
    class Actions(
        val settings: () -> Unit,
        val reload: () -> Unit,
        val nextPhoto: () -> Unit,
        val home: () -> Unit,
        val nextTheme: () -> Unit,
    )

    private val main = Handler(Looper.getMainLooper())
    private var timeFormat = "HH:mm"
    private var palette = PALETTES.getValue("day")
    private var weather: WeatherNow? = null
    private val time = TextView(context).apply {
        textSize = 76f
        typeface = Fonts.clock(context)
        includeFontPadding = false
        setShadowLayer(context.dpf(14f), 0f, context.dpf(2f), 0x40000000)
    }
    private val tick = object : Runnable {
        override fun run() {
            showTime()
            main.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    private val date = TextClock(context).apply {
        showLongDate()
        textSize = 17f
        typeface = Fonts.hanken(context, 600)
        setTextColor(Tokens.textPrimary)
        setShadowLayer(context.dpf(8f), 0f, context.dpf(1f), 0x59000000)
    }
    private val weatherIcon = ImageView(context)
    private val weatherText = TextView(context).apply {
        textSize = 17f
        typeface = Fonts.hanken(context, 600)
        setTextColor(Tokens.textPrimary)
        setShadowLayer(context.dpf(8f), 0f, context.dpf(1f), 0x59000000)
    }
    private val weatherLine = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        visibility = View.GONE
        addView(weatherIcon, LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { marginEnd = context.dp(8) })
        addView(weatherText)
    }

    // The forecast card: the next hours over the next days.
    private val hoursRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val daysRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val forecast = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        // Selectable: its icons play while it has focus, and rest otherwise.
        isFocusable = true
        background = StateListDrawable().apply {
            // A deep gradient under the text, so it reads on any background.
            addState(intArrayOf(android.R.attr.state_focused), forecastCard(context).apply { setStroke(context.dp(2), 0xE6FFFFFF.toInt()) })
            addState(intArrayOf(), forecastCard(context).apply { setStroke(context.dp(1), Glass.EDGE) })
        }
        setOnFocusChangeListener { _, focused -> playForecast(focused) }
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        visibility = View.GONE
        addView(hoursRow)
        addView(FlatView(context).apply { background = ColorDrawable(Glass.EDGE) },
            LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)).apply { setMargins(0, context.dp(8), 0, context.dp(8)) })
        addView(daysRow)
    }

    // The top-right bar and the inputs drop-down.
    private val inputsPill: LinearLayout = Glass.pill(context, R.drawable.ic_input, context.getString(R.string.sky_inputs)) {
        toggleInputs()
    }
    private val gear = Glass.pill(context, R.drawable.ic_settings, context.getString(R.string.sky_app_settings)) { actions.settings() }
    private val androidSettings = Glass.pill(context, R.drawable.ic_tv, context.getString(R.string.sky_android_settings)) { QuickActions.settings(context) }
    private val inputMenu = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = Glass.panel(context, 22f, 0xB3202428.toInt())
        setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        visibility = View.GONE
    }
    private var inputSignature = ""

    private val soundTile = QuickTile(context, R.drawable.ic_volume, context.getString(R.string.quick_sound), context.getString(R.string.quick_sound_sub)) {
        QuickActions.sound(context) { mode -> it.subtitle.text = mode }
    }
    private val pictureTile = QuickTile(context, R.drawable.ic_picture, context.getString(R.string.quick_picture), context.getString(R.string.quick_picture_sub)) {
        QuickActions.picture(context) { mode -> it.subtitle.text = mode }
    }
    private val wifiTile = QuickTile(context, R.drawable.ic_wifi, context.getString(R.string.quick_wifi), "") { QuickActions.wifi(context) }
    private val volume = VolumeSlider(context)

    // Network speed, memory and storage under the panel, refreshed every few seconds.
    private val status = TextView(context).apply {
        typeface = Fonts.hanken(context, 500)
        setTextColor(0xE6FFFFFF.toInt())
    }
    private val info = cloud.samlo.couch.data.SystemInfo(context)
    private val statusTick = object : Runnable {
        override fun run() {
            showStatus()
            main.postDelayed(this, STATUS_MS)
        }
    }

    private val panel = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(GridLayout(context).apply {
            columnCount = 2
            addView(soundTile, tileParams())
            addView(pictureTile, tileParams())
            addView(wifiTile, tileParams())
            addView(QuickTile(context, R.drawable.ic_moon, context.getString(R.string.quick_screensaver), context.getString(R.string.quick_screensaver_sub)) {
                QuickActions.screensaver(context)
            }, tileParams())
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(labelled(R.drawable.ic_apps, R.string.sky_apps) { QuickActions.apps(context) }, roundParams())
            addView(labelled(R.drawable.ic_folder, R.string.sky_files) { QuickActions.files(context) }, roundParams())
            addView(labelled(R.drawable.ic_home, R.string.sky_home) { actions.home() }, roundParams())
            addView(labelled(R.drawable.ic_refresh, R.string.sky_refresh) { actions.reload() }, roundParams())
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(volume, LinearLayout.LayoutParams(0, context.dp(50), 1f).apply { setMargins(context.dp(5), 0, context.dp(10), 0) })
            addView(round(R.drawable.ic_mute, R.string.sky_mute) { volume.toggleMute() },
                LinearLayout.LayoutParams(context.dp(50), context.dp(50)).apply { marginEnd = context.dp(5) })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(6) })
        addView(status.apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setShadowLayer(context.dpf(6f), 0f, context.dpf(1f), 0x59000000)
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })
    }

    /** The dock's glass, behind the rows; the home screen sizes and places it around the first row. */
    val dock = FlatView(context).apply { background = Glass.panel(context, 28f, 0x24FFFFFF) }

    init {
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(time)
            addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
            addView(weatherLine, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
            addView(forecast, LinearLayout.LayoutParams(context.dp(420), WRAP_CONTENT).apply { topMargin = context.dp(12) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(inputsPill, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(36)))
                addView(gear, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(36)).apply { marginStart = context.dp(10) })
                addView(androidSettings, LinearLayout.LayoutParams(WRAP_CONTENT, context.dp(36)).apply { marginStart = context.dp(10) })
            })
            addView(FrameLayout(context).apply {
                addView(panel, LayoutParams(context.dp(372), WRAP_CONTENT, Gravity.TOP or Gravity.END))
                // Over the panel while open.
                addView(inputMenu, LayoutParams(context.dp(200), WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { rightMargin = context.dp(46) })
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(10) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        // Remote paths with no dead ends: the forecast card leads up to the bar and right to the panel.
        for (v in listOf(inputsPill, soundTile, volume, forecast)) v.id = View.generateViewId()
        forecast.nextFocusUpId = inputsPill.id
        forecast.nextFocusRightId = soundTile.id

        // The drop-down closes once focus leaves it and its pill, like a menu.
        viewTreeObserver.addOnGlobalFocusChangeListener { _, focused ->
            if (inputMenu.visibility != View.VISIBLE) return@addOnGlobalFocusChangeListener
            if (focused != null && focused !== inputsPill && !inputMenu.holds(focused)) closeInputs(focusPill = false)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(tick)
        tick.run()
        main.removeCallbacks(statusTick)
        info.resetRates()
        statusTick.run()
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(tick)
        main.removeCallbacks(statusTick)
        super.onDetachedFromWindow()
    }

    /**
     * Up from anywhere in the dock lands on the controls above, the forecast card on the left
     * half and the volume slider on the right, so the top is one press away from every app.
     */
    fun focusFromDock(dockFocus: View): Boolean {
        val loc = IntArray(2)
        dockFocus.getLocationOnScreen(loc)
        val left = loc[0] + dockFocus.width / 2 < resources.displayMetrics.widthPixels / 2
        val target = if (left && forecast.visibility == View.VISIBLE) forecast else volume
        return target.requestFocus()
    }

    /** Back closes the inputs drop-down; true when the key was used. */
    fun consumesKey(event: KeyEvent): Boolean {
        if (inputMenu.visibility != View.VISIBLE || event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if (event.action == KeyEvent.ACTION_UP) closeInputs()
        return true
    }

    /** The TV's inputs, in the drop-down. Rebuilt only when they change, so focus stays put. */
    fun showInputs(tiles: List<Tile>) {
        val signature = tiles.joinToString(";") { it.signature }
        if (signature == inputSignature) return
        inputSignature = signature
        inputMenu.removeAllViews()
        for (tile in tiles) {
            val pill = Glass.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) {
                closeInputs(focusPill = false)
                tile.launch(context)
            }
            if (tile.dimmed) pill.alpha = 0.55f
            inputMenu.addView(pill, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(40)).apply { setMargins(context.dp(4), context.dp(3), context.dp(4), context.dp(3)) })
        }
        inputsPill.visibility = if (tiles.isEmpty()) View.GONE else View.VISIBLE
    }

    fun applyLook(config: Config, timeFormat: String) {
        this.timeFormat = timeFormat
        showTime()
        date.visibility = if (config.showDate) View.VISIBLE else View.GONE
    }

    fun showWeather(now: WeatherNow?) {
        weather = now
        showTime()
        if (now == null) {
            weatherLine.visibility = View.GONE
            forecast.visibility = View.GONE
            return
        }
        weatherIcon.setImageResource(now.icon)
        if (animating) (weatherIcon.drawable as? Animatable)?.start()
        val label = if (now.condition.label != 0) context.getString(now.condition.label) else ""
        weatherText.text = context.getString(R.string.weather_line, now.temperature, label, now.place)
        weatherLine.visibility = View.VISIBLE

        hoursRow.removeAllViews()
        for (hour in now.hours.take(FORECAST)) hoursRow.addView(hourColumn(hour), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        daysRow.removeAllViews()
        for (day in now.days.drop(1).take(FORECAST)) daysRow.addView(dayColumn(day), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        hoursRow.visibility = if (hoursRow.childCount == 0) View.GONE else View.VISIBLE
        daysRow.visibility = if (daysRow.childCount == 0) View.GONE else View.VISIBLE
        forecast.visibility = if (hoursRow.childCount + daysRow.childCount == 0) View.GONE else View.VISIBLE
        playForecast(forecast.hasFocus())
    }

    private fun playForecast(on: Boolean) {
        for (row in listOf(hoursRow, daysRow)) for (i in 0 until row.childCount) {
            val icon = ((row.getChildAt(i) as ViewGroup).getChildAt(1) as ImageView).drawable as? Animatable ?: continue
            if (on) icon.start() else icon.stop()
        }
    }

    private var animating = false

    fun animateWeather(on: Boolean) {
        animating = on
        (weatherIcon.drawable as? Animatable)?.let { if (on) it.start() else it.stop() }
    }

    /** Called on every return home: the volume may have changed while away. */
    fun refresh() {
        volume.refresh()
        wifiTile.subtitle.text = QuickActions.wifiName(context) ?: context.getString(R.string.quick_wifi_off)
        showTime()
    }

    /** Each digit takes the next colour of the time of day's palette; the colon stays muted. */
    private fun showTime() {
        val c = Calendar.getInstance()
        palette = PALETTES[SkyPhotos.timeOf(c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE), weather)] ?: palette
        val text = SimpleDateFormat(timeFormat, Locale.getDefault()).format(Date(c.timeInMillis))
        val spans = SpannableString(text)
        var digit = 0
        for (i in text.indices) {
            val color = if (text[i].isDigit()) palette[digit++ % palette.size] else COLON
            spans.setSpan(ForegroundColorSpan(color), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        time.text = spans
    }

    /** "↓ 1.2 ↑ 0.3 MB/s · RAM 62% · 3.1 GB free". */
    private fun showStatus() {
        val r = info.read()
        val rx = r.rxBytesPerSec
        val tx = r.txBytesPerSec
        val speed = if (rx == null || tx == null) "↓ – ↑ –" else {
            val mega = maxOf(rx, tx) >= 1_000_000
            val scale = if (mega) 1_048_576.0 else 1024.0
            val f = { v: Long -> String.format(Locale.getDefault(), if (v / scale >= 100) "%.0f" else "%.1f", v / scale) }
            "↓ ${f(rx)} ↑ ${f(tx)} " + if (mega) "MB/s" else "KB/s"
        }
        status.text = context.getString(R.string.sky_status, speed, r.memoryUsedPercent,
            android.text.format.Formatter.formatShortFileSize(context, r.storageFreeBytes))
    }

    private fun toggleInputs() {
        if (inputMenu.visibility == View.VISIBLE) {
            closeInputs()
            return
        }
        if (inputMenu.childCount == 0) return
        inputMenu.fadeIn()
        inputMenu.post { inputMenu.getChildAt(0)?.requestFocus() }
    }

    private fun closeInputs(focusPill: Boolean = true) {
        inputMenu.visibility = View.GONE
        if (focusPill) inputsPill.requestFocus()
    }

    private fun ViewGroup.holds(view: View): Boolean {
        var v: View? = view
        while (v != null) {
            if (v === this) return true
            v = v.parent as? View
        }
        return false
    }

    private fun hourColumn(hour: WeatherHour) = column(
        String.format(Locale.ROOT, "%02d:00", hour.hour),
        hour.icon,
        context.getString(R.string.sky_degrees, hour.temperature),
        if (hour.rainChance > 0) context.getString(R.string.sky_rain, hour.rainChance) else " ",
        RAIN,
    )

    private fun dayColumn(day: WeatherDay) = column(
        weekday(day.date),
        day.condition.icon(true),
        context.getString(R.string.sky_high_low, day.high, day.low),
        if (day.rainChance > 0) context.getString(R.string.sky_rain, day.rainChance) else " ",
        RAIN,
    )

    private fun forecastCard(context: Context) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(0xD91E2A3E.toInt(), 0xD92C3B5A.toInt()),
    ).apply { cornerRadius = context.dpf(22f) }

    private fun column(top: String, icon: Int, value: String, sub: String, subColor: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(label(12f, "sans-serif", 0xD9FFFFFF.toInt(), top))
        // One size for every icon, hours and days alike, so the two rows line up.
        addView(ImageView(context).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(context.dp(26), context.dp(26)).apply { setMargins(0, context.dp(2), 0, context.dp(2)) })
        addView(label(13f, "sans-serif-medium", Tokens.textPrimary, value))
        addView(label(11f, "sans-serif", subColor, sub))
    }

    private fun weekday(iso: String): String = try {
        SimpleDateFormat("EEE", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso)!!)
    } catch (e: Exception) {
        ""
    }

    private fun label(size: Float, family: String, color: Int, value: String) = TextView(context).apply {
        textSize = size
        typeface = Fonts.hanken(context, if (family.endsWith("medium")) 600 else 400)
        setTextColor(color)
        includeFontPadding = false
        text = value
    }

    private fun round(icon: Int, label: Int, onClick: () -> Unit) = Glass.pill(context, icon, null) { onClick() }.apply {
        contentDescription = context.getString(label)
        background = Glass.focusable(context, 25f)
    }

    private fun roundParams() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
        setMargins(context.dp(5), context.dp(4), context.dp(5), 0)
    }

    /** A round button with its name under it, as on LG's home. */
    private fun labelled(icon: Int, label: Int, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(round(icon, label, onClick), LinearLayout.LayoutParams(context.dp(46), context.dp(46)))
        addView(label(11f, "sans-serif", 0xE6FFFFFF.toInt(), context.getString(label)).apply {
            setShadowLayer(context.dpf(4f), 0f, context.dpf(1f), 0x66000000)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
    }

    private fun tileParams() = GridLayout.LayoutParams().apply {
        width = context.dp(176)
        height = context.dp(54)
        setMargins(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
    }

    /** A quick-settings tile: icon, title and a muted second line, as on LG's home. */
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
                    typeface = Fonts.hanken(context, 600)
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
            typeface = Fonts.hanken(context, 600)
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
                    typeface = Fonts.hanken(context, 600)
                    setTextColor(Glass.content)
                    isDuplicateParentStateEnabled = true
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(value)
            }, fill())
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) placeFill() }
        }

        fun refresh() = show(QuickActions.volume(context))

        fun toggleMute() = show(QuickActions.toggleMute(context))

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
        const val COLON = 0xB3D0D6DC.toInt()
        const val RAIN = 0xFF9FD3FF.toInt()
        const val FORECAST = 7
        const val STATUS_MS = 3_000L

        /** Digit colours for each time of day, in turn; the first is the bold accent. */
        val PALETTES = mapOf(
            "dawn" to intArrayOf(0xFFFF8A80.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),
            "day" to intArrayOf(0xFF4FC3F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFB8C0C8.toInt()),
            "dusk" to intArrayOf(0xFFFF7043.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt()),
            "night" to intArrayOf(0xFFE5484D.toInt(), 0xFFFFFFFF.toInt(), 0xFFB8C0C8.toInt()),
        )
    }
}
