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
 * Modern Neumorphic sky home: tactile surfaces with high readability from 3 meters away.
 * Single-row slideshowing weather forecast (hours spaced by 2h alternating with 7 days),
 * perfectly symmetrical 372 dp top bar matching the 2x2 panel below, dedicated App Store,
 * Files, EShare, and AirPlay shortcuts, and full support for both Neumorphic (default) and
 * Glass style presets.
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
    private var currentStyle: String = Config.STYLE_NEUMORPHIC

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

    // The forecast card: single-row slideshow alternating between 7 hours (2h intervals) and 7 days.
    private val hoursRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val daysRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val forecastContent = FrameLayout(context).apply {
        addView(hoursRow, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(daysRow, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    private var showingDays = false
    private val weatherSlideTick = object : Runnable {
        override fun run() {
            toggleForecastSlide()
            main.postDelayed(this, SLIDE_INTERVAL_MS)
        }
    }

    private val forecast = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isFocusable = true
        isClickable = true
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        visibility = View.GONE
        addView(forecastContent)
        setOnFocusChangeListener { _, focused -> playForecast(focused) }
        setOnClickListener { toggleForecastSlide() }
    }

    // Top-right bar matching the 372 dp width of the quick panel below.
    private val inputsPill = topBarPill(R.drawable.ic_input, context.getString(R.string.sky_inputs)) {
        toggleInputs()
    }
    private val launcherPill = topBarPill(R.drawable.ic_tune, context.getString(R.string.sky_app_settings)) {
        actions.settings()
    }
    private val androidPill = topBarPill(R.drawable.ic_settings, context.getString(R.string.sky_android_settings)) {
        QuickActions.settings(context)
    }

    private val inputMenu = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(6), context.dp(6), context.dp(6), context.dp(6))
        visibility = View.GONE
    }
    private var inputSignature = ""

    private val soundTile = QuickTile(context, R.drawable.ic_volume, context.getString(R.string.quick_sound), context.getString(R.string.mode_standard)) { tile ->
        QuickActions.sound(context) { mode ->
            tile.subtitle.text = mode
        }
    }
    private val pictureTile = QuickTile(context, R.drawable.ic_picture, context.getString(R.string.quick_picture), context.getString(R.string.mode_standard)) { tile ->
        QuickActions.picture(context) { mode ->
            tile.subtitle.text = mode
        }
    }
    private val wifiTile = QuickTile(context, R.drawable.ic_wifi, context.getString(R.string.quick_wifi), "") {
        QuickActions.wifi(context)
    }
    private val screensaverTile = QuickTile(context, R.drawable.ic_moon, context.getString(R.string.quick_screensaver), context.getString(R.string.quick_screensaver_sub)) {
        QuickActions.screensaver(context)
    }

    private val storeBtn = round(R.drawable.ic_store, R.string.sky_store) { QuickActions.appStore(context) }
    private val filesBtn = round(R.drawable.ic_folder, R.string.sky_files) { QuickActions.files(context) }
    private val eshareBtn = round(R.drawable.ic_eshare, R.string.sky_eshare) { QuickActions.eshare(context) }
    private val airplayBtn = round(R.drawable.ic_airplay, R.string.sky_airplay) { QuickActions.airplay(context) }

    private val volume = VolumeSlider(context)
    private val muteBtn = round(R.drawable.ic_mute, R.string.sky_mute) { volume.toggleMute() }

    // Network speed, memory and storage under the panel, refreshed every few seconds.
    private val status = TextView(context).apply {
        typeface = Fonts.hanken(context, 500)
        setTextColor(0xE6FFFFFF.toInt())
        textSize = 12f
        gravity = Gravity.CENTER
        setShadowLayer(context.dpf(6f), 0f, context.dpf(1f), 0x59000000)
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
            addView(screensaverTile, tileParams())
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(labelled(storeBtn, R.string.sky_store), roundParams())
            addView(labelled(filesBtn, R.string.sky_files), roundParams())
            addView(labelled(eshareBtn, R.string.sky_eshare), roundParams())
            addView(labelled(airplayBtn, R.string.sky_airplay), roundParams())
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(6) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(volume, LinearLayout.LayoutParams(0, context.dp(48), 1f).apply { setMargins(context.dp(5), 0, context.dp(10), 0) })
            addView(muteBtn, LinearLayout.LayoutParams(context.dp(48), context.dp(48)).apply { marginEnd = context.dp(5) })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })
        addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })
    }

    /** The dock's background panel behind the first row. */
    val dock = FlatView(context).apply { background = Neumorphic.panel(context, 24f) }

    init {
        // Left Column: Clock, Date, Weather line, and compact single-row forecast
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

        // Right Column: Symmetrical 372 dp Top Bar & Quick Panel
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val h = context.dp(38)
                addView(inputsPill, LinearLayout.LayoutParams(0, h, 1f).apply { marginEnd = context.dp(4) })
                addView(launcherPill, LinearLayout.LayoutParams(0, h, 1f).apply { setMargins(context.dp(4), 0, context.dp(4), 0) })
                addView(androidPill, LinearLayout.LayoutParams(0, h, 1f).apply { marginStart = context.dp(4) })
            }, LinearLayout.LayoutParams(context.dp(372), WRAP_CONTENT))
            addView(FrameLayout(context).apply {
                addView(panel, LayoutParams(context.dp(372), WRAP_CONTENT, Gravity.TOP or Gravity.END))
                addView(inputMenu, LayoutParams(context.dp(200), WRAP_CONTENT, Gravity.TOP or Gravity.END).apply { rightMargin = context.dp(46) })
            }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(10) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        // D-pad Remote focus connections
        for (v in listOf(inputsPill, launcherPill, androidPill, soundTile, volume, forecast)) v.id = View.generateViewId()
        forecast.nextFocusUpId = inputsPill.id
        forecast.nextFocusRightId = soundTile.id
        soundTile.nextFocusLeftId = forecast.id
        soundTile.nextFocusUpId = launcherPill.id

        // The drop-down closes once focus leaves it and its pill
        viewTreeObserver.addOnGlobalFocusChangeListener { _, focused ->
            if (inputMenu.visibility != View.VISIBLE) return@addOnGlobalFocusChangeListener
            if (focused != null && focused !== inputsPill && !inputMenu.holds(focused)) closeInputs(focusPill = false)
        }

        updateStyle(Config.STYLE_NEUMORPHIC)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        main.removeCallbacks(tick)
        tick.run()
        main.removeCallbacks(statusTick)
        info.resetRates()
        statusTick.run()
        main.removeCallbacks(weatherSlideTick)
        main.postDelayed(weatherSlideTick, SLIDE_INTERVAL_MS)
    }

    override fun onDetachedFromWindow() {
        main.removeCallbacks(tick)
        main.removeCallbacks(statusTick)
        main.removeCallbacks(weatherSlideTick)
        super.onDetachedFromWindow()
    }

    /**
     * Up from anywhere in the dock lands on the controls above:
     * the forecast card on the left half and the volume slider on the right half.
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

    /** The TV's inputs in the drop-down. */
    fun showInputs(tiles: List<Tile>) {
        val signature = tiles.joinToString(";") { it.signature }
        if (signature == inputSignature) return
        inputSignature = signature
        inputMenu.removeAllViews()
        val isGlass = currentStyle == Config.STYLE_GLASS
        for (tile in tiles) {
            val pill = if (isGlass) {
                Glass.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) {
                    closeInputs(focusPill = false)
                    tile.launch(context)
                }
            } else {
                Neumorphic.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) {
                    closeInputs(focusPill = false)
                    tile.launch(context)
                }
            }
            if (tile.dimmed) pill.alpha = 0.55f
            inputMenu.addView(pill, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(40)).apply {
                setMargins(context.dp(4), context.dp(3), context.dp(4), context.dp(3))
            })
        }
        inputsPill.visibility = if (tiles.isEmpty()) View.GONE else View.VISIBLE
    }

    fun applyLook(config: Config, timeFormat: String) {
        this.timeFormat = timeFormat
        showTime()
        date.visibility = if (config.showDate) View.VISIBLE else View.GONE
        updateStyle(config.style)
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
        for (hour in now.hours.take(FORECAST)) {
            hoursRow.addView(hourColumn(hour), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        daysRow.removeAllViews()
        for (day in now.days.drop(1).take(FORECAST)) {
            daysRow.addView(dayColumn(day), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        showingDays = false
        hoursRow.visibility = if (hoursRow.childCount > 0) View.VISIBLE else View.GONE
        daysRow.visibility = View.GONE
        forecast.visibility = if (hoursRow.childCount + daysRow.childCount == 0) View.GONE else View.VISIBLE
        playForecast(forecast.hasFocus())
    }

    private fun toggleForecastSlide() {
        if (hoursRow.childCount == 0 || daysRow.childCount == 0) return
        showingDays = !showingDays
        hoursRow.visibility = if (showingDays) View.GONE else View.VISIBLE
        daysRow.visibility = if (showingDays) View.VISIBLE else View.GONE
        playForecast(forecast.hasFocus())
    }

    private fun playForecast(on: Boolean) {
        for (row in listOf(hoursRow, daysRow)) for (i in 0 until row.childCount) {
            val column = row.getChildAt(i) as? ViewGroup ?: continue
            val icon = (column.getChildAt(1) as? ImageView)?.drawable as? Animatable ?: continue
            if (on) icon.start() else icon.stop()
        }
    }

    private var animating = false

    fun animateWeather(on: Boolean) {
        animating = on
        (weatherIcon.drawable as? Animatable)?.let { if (on) it.start() else it.stop() }
    }

    fun refresh() {
        volume.refresh()
        wifiTile.subtitle.text = QuickActions.wifiName(context) ?: context.getString(R.string.quick_wifi_off)
        showTime()
    }

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
        status.text = context.getString(
            R.string.sky_status, speed, r.memoryUsedPercent,
            android.text.format.Formatter.formatShortFileSize(context, r.storageFreeBytes)
        )
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
    ).apply { cornerRadius = context.dpf(18f) }

    private fun column(top: String, icon: Int, value: String, sub: String, subColor: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(label(12f, "sans-serif", 0xD9FFFFFF.toInt(), top))
        addView(ImageView(context).apply {
            setImageResource(icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply { setMargins(0, context.dp(2), 0, context.dp(2)) })
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

    private fun round(icon: Int, label: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = context.getString(label)
        isFocusable = true
        isClickable = true
        setPadding(context.dp(12), context.dp(12), context.dp(12), context.dp(12))
        setOnClickListener { onClick() }
    }

    private fun roundParams() = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
        setMargins(context.dp(4), context.dp(4), context.dp(4), 0)
    }

    private fun labelled(btn: View, labelRes: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        addView(btn, LinearLayout.LayoutParams(context.dp(46), context.dp(46)))
        addView(label(11f, "sans-serif", 0xE6FFFFFF.toInt(), context.getString(labelRes)).apply {
            setShadowLayer(context.dpf(4f), 0f, context.dpf(1f), 0x66000000)
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
    }

    private fun tileParams() = GridLayout.LayoutParams().apply {
        width = context.dp(176)
        height = context.dp(54)
        setMargins(context.dp(5), context.dp(5), context.dp(5), context.dp(5))
    }

    private fun topBarPill(iconRes: Int, text: String, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isDuplicateParentStateEnabled = true
        }
        val label = TextView(context).apply {
            this.text = text
            textSize = 13f
            typeface = Fonts.hanken(context, 600)
            isDuplicateParentStateEnabled = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        addView(icon, LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply { marginEnd = context.dp(6) })
        addView(label)
        setOnClickListener { onClick() }
    }

    fun updateStyle(style: String) {
        currentStyle = style
        val isGlass = style == Config.STYLE_GLASS
        val content = if (isGlass) Glass.content else Neumorphic.content
        val contentMuted = if (isGlass) Glass.contentMuted else Neumorphic.contentMuted

        // Top bar pills
        for (pill in listOf(inputsPill, launcherPill, androidPill)) {
            pill.background = if (isGlass) Glass.focusable(context, 14f) else Neumorphic.focusable(context, 12f)
            (pill.getChildAt(0) as? ImageView)?.imageTintList = content
            (pill.getChildAt(1) as? TextView)?.setTextColor(content)
        }

        // Forecast card
        forecast.background = if (isGlass) {
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), forecastCard(context).apply { setStroke(context.dp(2), 0xE6FFFFFF.toInt()) })
                addState(intArrayOf(), forecastCard(context).apply { setStroke(context.dp(1), Glass.EDGE) })
            }
        } else {
            Neumorphic.focusable(context, 18f)
        }

        // Quick tiles
        for (tile in listOf(soundTile, pictureTile, wifiTile, screensaverTile)) {
            tile.restyle(isGlass)
        }

        // Round action buttons
        for (btn in listOf(storeBtn, filesBtn, eshareBtn, airplayBtn, muteBtn)) {
            btn.background = if (isGlass) Glass.focusable(context, 23f) else Neumorphic.focusable(context, 23f)
            btn.imageTintList = content
        }

        // Volume slider
        volume.restyle(isGlass)

        // Dock & drop-down panel
        dock.background = if (isGlass) Glass.panel(context, 28f, 0x24FFFFFF) else Neumorphic.panel(context, 24f)
        inputMenu.background = if (isGlass) Glass.panel(context, 22f, 0xB3202428.toInt()) else Neumorphic.panel(context, 18f)
    }

    /** A quick-settings tile: icon, title and full subtitle written out without truncation. */
    private class QuickTile(
        context: Context,
        val iconRes: Int,
        val titleText: String,
        subText: String,
        val onClick: (QuickTile) -> Unit
    ) : LinearLayout(context) {
        val iconView = ImageView(context).apply {
            setImageResource(iconRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isDuplicateParentStateEnabled = true
        }
        val title = TextView(context).apply {
            text = titleText
            textSize = 14f
            typeface = Fonts.hanken(context, 600)
            isDuplicateParentStateEnabled = true
            maxLines = 1
        }
        val subtitle = TextView(context).apply {
            text = subText
            textSize = 12f
            isDuplicateParentStateEnabled = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isClickable = true
            setPadding(context.dp(14), 0, context.dp(12), 0)
            addView(iconView, LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(10) })
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                isDuplicateParentStateEnabled = true
                addView(title)
                addView(subtitle)
            })
            setOnClickListener { onClick(this) }
            restyle(false)
        }

        fun restyle(isGlass: Boolean) {
            background = if (isGlass) Glass.focusable(context, 27f) else Neumorphic.focusable(context, 16f)
            val content = if (isGlass) Glass.content else Neumorphic.content
            val contentMuted = if (isGlass) Glass.contentMuted else Neumorphic.contentMuted
            iconView.imageTintList = content
            title.setTextColor(content)
            subtitle.setTextColor(contentMuted)
        }
    }

    /** Volume slider with proportional visual fill and remote D-pad step adjustment. */
    private class VolumeSlider(context: Context) : FrameLayout(context) {
        private val fill = FlatView(context).apply { isDuplicateParentStateEnabled = true }
        private val iconView = ImageView(context).apply {
            setImageResource(R.drawable.ic_volume)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isDuplicateParentStateEnabled = true
        }
        private val titleView = TextView(context).apply {
            setText(R.string.sky_volume)
            textSize = 14f
            typeface = Fonts.hanken(context, 600)
            isDuplicateParentStateEnabled = true
        }
        private val value = TextView(context).apply {
            textSize = 14f
            typeface = Fonts.hanken(context, 600)
            isDuplicateParentStateEnabled = true
        }
        private var level = 0

        init {
            isFocusable = true
            isClickable = true
            contentDescription = context.getString(R.string.sky_volume)
            addView(fill, LayoutParams(0, MATCH_PARENT))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isDuplicateParentStateEnabled = true
                setPadding(context.dp(14), 0, context.dp(16), 0)
                addView(iconView, LinearLayout.LayoutParams(context.dp(22), context.dp(22)).apply { marginEnd = context.dp(10) })
                addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(value)
            }, fill())
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or_, _ -> if (r - l != or_ - ol) placeFill() }
            restyle(false)
        }

        fun restyle(isGlass: Boolean) {
            background = if (isGlass) Glass.focusable(context, 24f) else Neumorphic.focusable(context, 16f)
            val content = if (isGlass) Glass.content else Neumorphic.content
            iconView.imageTintList = content
            titleView.setTextColor(content)
            value.setTextColor(content)
            fill.background = StateListDrawable().apply {
                if (isGlass) {
                    addState(intArrayOf(android.R.attr.state_focused), rounded(context, 0x2615171A, 24f))
                    addState(intArrayOf(), rounded(context, 0x33FFFFFF, 24f))
                } else {
                    addState(intArrayOf(android.R.attr.state_focused), rounded(context, 0x4060A5FA.toInt(), 16f))
                    addState(intArrayOf(), rounded(context, 0x24FFFFFF, 16f))
                }
            }
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

        private fun rounded(context: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
            cornerRadius = context.dpf(radiusDp)
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
        const val SLIDE_INTERVAL_MS = 6_000L

        /** Digit colours for each time of day, in turn; the first is the bold accent. */
        val PALETTES = mapOf(
            "dawn" to intArrayOf(0xFFFF8A80.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFCCBC.toInt()),
            "day" to intArrayOf(0xFF4FC3F7.toInt(), 0xFFFFFFFF.toInt(), 0xFFB8C0C8.toInt()),
            "dusk" to intArrayOf(0xFFFF7043.toInt(), 0xFFFFFFFF.toInt(), 0xFFF8BBD0.toInt()),
            "night" to intArrayOf(0xFFE5484D.toInt(), 0xFFFFFFFF.toInt(), 0xFFB8C0C8.toInt()),
        )
    }
}
