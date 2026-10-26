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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import android.widget.Toast
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
 * Apple-style unified weather widget with city, temperature hero, condition badge, two-dot
 * hourly vs daily indicator, 2-pill symmetrical top bar, and 4-row quick panel with inline TV inputs.
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

    // --- Apple-Style Unified Weather Widget ---
    private val weatherCity = TextView(context).apply {
        textSize = 18f
        typeface = Fonts.hanken(context, 600)
        setTextColor(0xCCFFFFFF.toInt())
    }
    private val weatherTemp = TextView(context).apply {
        textSize = 48f
        typeface = Fonts.hanken(context, 700)
        setTextColor(0xFFFFFFFF.toInt())
        includeFontPadding = false
    }
    private val weatherRange = TextView(context).apply {
        textSize = 13f
        typeface = Fonts.hanken(context, 500)
        setTextColor(0x80FFFFFF.toInt())
    }
    private val weatherIcon = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    private val weatherCondition = TextView(context).apply {
        textSize = 14f
        typeface = Fonts.hanken(context, 600)
        setTextColor(0xFFFFFFFF.toInt())
    }
    private val weatherBadge = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(context.dp(8), context.dp(4), context.dp(10), context.dp(4))
        background = GradientDrawable().apply {
            cornerRadius = context.dpf(12f)
            setColor(0x14FFFFFF)
        }
        addView(weatherIcon, LinearLayout.LayoutParams(context.dp(20), context.dp(20)).apply { marginEnd = context.dp(6) })
        addView(weatherCondition)
    }
    private val modeLabel = TextView(context).apply {
        text = context.getString(R.string.weather_hourly)
        textSize = 11f
        typeface = Fonts.hanken(context, 700)
        setTextColor(0x80FFFFFF.toInt())
    }
    private val dot1 = View(context)
    private val dot2 = View(context)
    private val paginationDots = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(modeLabel, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginEnd = context.dp(6) })
        addView(dot1, LinearLayout.LayoutParams(context.dp(7), context.dp(7)))
        addView(dot2, LinearLayout.LayoutParams(context.dp(7), context.dp(7)).apply { marginStart = context.dp(5) })
    }
    private val weatherDivider = View(context).apply {
        setBackgroundColor(0x1AFFFFFF)
    }

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

    private val weatherCard = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        isFocusable = true
        isClickable = true
        setPadding(context.dp(18), context.dp(16), context.dp(18), context.dp(14))
        visibility = View.GONE

        // Top Row: City & Temp on left, Badge & Pagination dots on right
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(weatherCity)
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(weatherTemp)
                    addView(weatherRange, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = context.dp(8) })
                })
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.END
                addView(weatherBadge)
                addView(paginationDots, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(6) })
            })
        })

        // Divider
        addView(weatherDivider, LinearLayout.LayoutParams(MATCH_PARENT, context.dp(1)).apply {
            setMargins(0, context.dp(10), 0, context.dp(10))
        })

        // Bottom Row: 7-Item Forecast
        addView(forecastContent, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        setOnFocusChangeListener { _, focused -> playForecast(focused) }
        setOnClickListener {
            try {
                toggleForecastSlide()
            } catch (e: Throwable) {
            }
        }
    }

    // --- Top Bar: 2 Symmetrical Pills ---
    private val launcherPill = topBarPill(R.drawable.ic_tune, context.getString(R.string.sky_launcher_settings)) {
        try {
            actions.settings()
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    private val tvSettingsPill = topBarPill(R.drawable.ic_settings, context.getString(R.string.sky_tv_settings)) {
        try {
            QuickActions.tvSettings(context)
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // --- Quick Panel: 4 Logical Rows ---
    // Row 1: Wi-Fi, Files
    private val wifiTile = QuickTile(context, R.drawable.ic_wifi, context.getString(R.string.quick_wifi), "") {
        try {
            QuickActions.wifi(context)
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    private val filesTile = QuickTile(context, R.drawable.ic_folder, context.getString(R.string.sky_files), context.getString(R.string.section_files)) {
        try {
            QuickActions.files(context)
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // Row 2: Sound, Picture
    private val soundTile = QuickTile(context, R.drawable.ic_volume, context.getString(R.string.quick_sound), context.getString(R.string.mode_standard)) { tile ->
        try {
            QuickActions.sound(context) { mode ->
                tile.subtitle.text = mode
            }
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    private val pictureTile = QuickTile(context, R.drawable.ic_picture, context.getString(R.string.quick_picture), context.getString(R.string.mode_standard)) { tile ->
        try {
            QuickActions.picture(context) { mode ->
                tile.subtitle.text = mode
            }
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // Row 3: EShare, AirPlay
    private val eshareTile = QuickTile(context, R.drawable.ic_eshare, context.getString(R.string.sky_eshare), "Screen Mirror") {
        try {
            QuickActions.eshare(context)
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }
    private val airplayTile = QuickTile(context, R.drawable.ic_airplay, context.getString(R.string.sky_airplay), "Cast Receiver") {
        try {
            QuickActions.airplay(context)
        } catch (e: Throwable) {
            Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    // Row 4: Inline TV Inputs Strip
    private val inputsHeader = TextView(context).apply {
        text = context.getString(R.string.sky_inputs).uppercase()
        textSize = 11f
        typeface = Fonts.hanken(context, 700)
        setTextColor(0x80FFFFFF.toInt())
        setPadding(context.dp(4), 0, 0, context.dp(4))
    }
    private val inputsRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    private val inputsSection = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        addView(inputsHeader)
        addView(inputsRow, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }
    private var inputSignature = ""

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
        val tileH = context.dp(56)
        val rowGap = context.dp(8)
        val tileGap = context.dp(4)

        // Row 1: Wi-Fi, Files
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(wifiTile, LinearLayout.LayoutParams(0, tileH, 1f).apply { marginEnd = tileGap })
            addView(filesTile, LinearLayout.LayoutParams(0, tileH, 1f).apply { marginStart = tileGap })
        })
        // Row 2: Sound, Picture
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(soundTile, LinearLayout.LayoutParams(0, tileH, 1f).apply { marginEnd = tileGap })
            addView(pictureTile, LinearLayout.LayoutParams(0, tileH, 1f).apply { marginStart = tileGap })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = rowGap })
        // Row 3: EShare, AirPlay
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(eshareTile, LinearLayout.LayoutParams(0, tileH, 1f).apply { marginEnd = tileGap })
            addView(airplayTile, LinearLayout.LayoutParams(0, tileH, 1f).apply { marginStart = tileGap })
        }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = rowGap })
        // Row 4: Inline TV Inputs
        addView(inputsSection, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = rowGap + context.dp(2) })
        // Status line
        addView(status, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = context.dp(8) })
    }

    /** The dock's background panel behind the first row. */
    val dock = FlatView(context).apply { background = Neumorphic.panel(context, 24f) }

    init {
        // Left Column: Clock, Date, Apple-Style Weather Card
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(time)
            addView(date, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = context.dp(4) })
            addView(weatherCard, LinearLayout.LayoutParams(context.dp(480), WRAP_CONTENT).apply { topMargin = context.dp(18) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
            leftMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        // Right Column: Symmetrical 390 dp Top Bar (2 pills) & Quick Panel (4 rows)
        val panelW = context.dp(390)
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val h = context.dp(42)
                addView(launcherPill, LinearLayout.LayoutParams(0, h, 1f).apply { marginEnd = context.dp(5) })
                addView(tvSettingsPill, LinearLayout.LayoutParams(0, h, 1f).apply { marginStart = context.dp(5) })
            }, LinearLayout.LayoutParams(panelW, WRAP_CONTENT))
            addView(panel, LinearLayout.LayoutParams(panelW, WRAP_CONTENT).apply { topMargin = context.dp(12) })
        }, LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
            rightMargin = context.dp(Tokens.OVERSCAN_H)
            topMargin = context.dp(Tokens.OVERSCAN_V)
        })

        // D-pad Remote focus connections
        for (v in listOf(launcherPill, tvSettingsPill, wifiTile, filesTile, soundTile, pictureTile, eshareTile, airplayTile, weatherCard)) {
            v.id = View.generateViewId()
        }
        weatherCard.nextFocusRightId = launcherPill.id
        weatherCard.nextFocusUpId = launcherPill.id

        launcherPill.nextFocusLeftId = weatherCard.id
        launcherPill.nextFocusRightId = tvSettingsPill.id
        launcherPill.nextFocusDownId = wifiTile.id

        tvSettingsPill.nextFocusLeftId = launcherPill.id
        tvSettingsPill.nextFocusDownId = filesTile.id

        wifiTile.nextFocusLeftId = weatherCard.id
        wifiTile.nextFocusUpId = launcherPill.id
        wifiTile.nextFocusRightId = filesTile.id
        wifiTile.nextFocusDownId = soundTile.id

        filesTile.nextFocusLeftId = wifiTile.id
        filesTile.nextFocusUpId = tvSettingsPill.id
        filesTile.nextFocusDownId = pictureTile.id

        soundTile.nextFocusLeftId = weatherCard.id
        soundTile.nextFocusUpId = wifiTile.id
        soundTile.nextFocusRightId = pictureTile.id
        soundTile.nextFocusDownId = eshareTile.id

        pictureTile.nextFocusLeftId = soundTile.id
        pictureTile.nextFocusUpId = filesTile.id
        pictureTile.nextFocusDownId = airplayTile.id

        eshareTile.nextFocusLeftId = weatherCard.id
        eshareTile.nextFocusUpId = soundTile.id
        eshareTile.nextFocusRightId = airplayTile.id

        airplayTile.nextFocusLeftId = eshareTile.id
        airplayTile.nextFocusUpId = pictureTile.id

        updateDots()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tick.run()
        statusTick.run()
        if (forecastContent.visibility == View.VISIBLE) main.postDelayed(weatherSlideTick, SLIDE_INTERVAL_MS)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        main.removeCallbacks(tick)
        main.removeCallbacks(statusTick)
        main.removeCallbacks(weatherSlideTick)
    }

    /**
     * Up from the dock jumps directly to the sky controls:
     * the weather card on the left half and the inline inputs/panel on the right half.
     */
    fun focusFromDock(dockFocus: View): Boolean {
        val loc = IntArray(2)
        dockFocus.getLocationOnScreen(loc)
        val left = loc[0] + dockFocus.width / 2 < resources.displayMetrics.widthPixels / 2
        val target = if (left && weatherCard.visibility == View.VISIBLE) {
            weatherCard
        } else {
            inputsRow.getChildAt(0) ?: wifiTile
        }
        return target.requestFocus()
    }

    fun consumesKey(event: KeyEvent): Boolean = false

    /** The TV's inputs displayed directly in the inline strip. */
    fun showInputs(tiles: List<Tile>) {
        val signature = tiles.joinToString(";") { it.signature }
        if (signature == inputSignature) return
        inputSignature = signature
        inputsRow.removeAllViews()
        val isGlass = currentStyle == Config.STYLE_GLASS
        inputsSection.visibility = if (tiles.isEmpty()) View.GONE else View.VISIBLE

        for (tile in tiles) {
            val chip = if (isGlass) {
                Glass.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) {
                    try {
                        tile.launch(context)
                    } catch (e: Throwable) {
                        Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Neumorphic.pill(context, tile.iconRes.takeIf { it != 0 } ?: R.drawable.ic_input, tile.label) {
                    try {
                        tile.launch(context)
                    } catch (e: Throwable) {
                        Toast.makeText(context, R.string.quick_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            if (tile.dimmed) chip.alpha = 0.55f
            inputsRow.addView(chip, LinearLayout.LayoutParams(0, context.dp(38), 1f).apply {
                setMargins(context.dp(3), 0, context.dp(3), 0)
            })
        }

        // Link focus down from Row 3 to the input chips
        if (inputsRow.childCount > 0) {
            val first = inputsRow.getChildAt(0)
            val last = inputsRow.getChildAt(inputsRow.childCount - 1)
            eshareTile.nextFocusDownId = first.id
            airplayTile.nextFocusDownId = last.id
            for (i in 0 until inputsRow.childCount) {
                inputsRow.getChildAt(i).nextFocusUpId = if (i < inputsRow.childCount / 2) eshareTile.id else airplayTile.id
            }
        }
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
            weatherCard.visibility = View.GONE
            return
        }
        weatherCity.text = now.place
        weatherTemp.text = context.getString(R.string.sky_degrees, now.temperature)
        weatherRange.text = context.getString(R.string.sky_range_hl, now.high, now.low)
        weatherIcon.setImageResource(now.icon)
        weatherCondition.text = if (now.condition.label != 0) context.getString(now.condition.label) else ""
        if (animating) (weatherIcon.drawable as? Animatable)?.start()

        hoursRow.removeAllViews()
        for (hour in now.hours.take(FORECAST)) {
            hoursRow.addView(hourColumn(hour), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }
        daysRow.removeAllViews()
        for (day in now.days.drop(1).take(FORECAST)) {
            daysRow.addView(dayColumn(day), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        }

        if (hoursRow.childCount > 0) {
            showingDays = false
            hoursRow.visibility = View.VISIBLE
            daysRow.visibility = View.GONE
            weatherCard.visibility = View.VISIBLE
        } else if (daysRow.childCount > 0) {
            showingDays = true
            hoursRow.visibility = View.GONE
            daysRow.visibility = View.VISIBLE
            weatherCard.visibility = View.VISIBLE
        } else {
            weatherCard.visibility = View.GONE
        }
        updateDots()

        if (weatherCard.visibility == View.VISIBLE) {
            main.removeCallbacks(weatherSlideTick)
            main.postDelayed(weatherSlideTick, SLIDE_INTERVAL_MS)
        }
        playForecast(weatherCard.hasFocus())
    }

    private fun toggleForecastSlide() {
        if (hoursRow.childCount == 0 && daysRow.childCount == 0) return
        if (hoursRow.childCount == 0) {
            showingDays = true
            hoursRow.visibility = View.GONE
            daysRow.visibility = View.VISIBLE
            updateDots()
            return
        }
        if (daysRow.childCount == 0) {
            showingDays = false
            hoursRow.visibility = View.VISIBLE
            daysRow.visibility = View.GONE
            updateDots()
            return
        }
        showingDays = !showingDays
        hoursRow.visibility = if (showingDays) View.GONE else View.VISIBLE
        daysRow.visibility = if (showingDays) View.VISIBLE else View.GONE
        updateDots()
        playForecast(weatherCard.hasFocus())
    }

    private fun updateDots() {
        val activeDot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Tokens.accent)
        }
        val inactiveDot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x40FFFFFF)
        }
        dot1.background = if (showingDays) inactiveDot else activeDot
        dot2.background = if (showingDays) activeDot else inactiveDot
        modeLabel.text = context.getString(if (showingDays) R.string.weather_daily else R.string.weather_hourly)
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
    ).apply { cornerRadius = context.dpf(20f) }

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
            textSize = 14f
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
        inputSignature = ""
        val isGlass = style == Config.STYLE_GLASS
        val content = if (isGlass) Glass.content else Neumorphic.content
        val contentMuted = if (isGlass) Glass.contentMuted else Neumorphic.contentMuted

        // Top bar pills
        for (pill in listOf(launcherPill, tvSettingsPill)) {
            pill.background = if (isGlass) Glass.focusable(context, 16f) else Neumorphic.focusable(context, 14f)
            (pill.getChildAt(0) as? ImageView)?.imageTintList = content
            (pill.getChildAt(1) as? TextView)?.setTextColor(content)
        }

        // Apple-style Weather card
        weatherCard.background = if (isGlass) {
            StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), forecastCard(context).apply { setStroke(context.dp(2), 0xE6FFFFFF.toInt()) })
                addState(intArrayOf(), forecastCard(context).apply { setStroke(context.dp(1), Glass.EDGE) })
            }
        } else {
            Neumorphic.focusable(context, 22f)
        }

        // Quick tiles
        for (tile in listOf(wifiTile, filesTile, soundTile, pictureTile, eshareTile, airplayTile)) {
            tile.restyle(isGlass)
        }

        // Dock & panel
        dock.background = if (isGlass) Glass.panel(context, 28f, 0x24FFFFFF) else Neumorphic.panel(context, 24f)
        panel.background = if (isGlass) Glass.panel(context, 24f, 0x33FFFFFF) else Neumorphic.panel(context, 24f)
        updateDots()
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
            background = if (isGlass) Glass.focusable(context, 16f) else Neumorphic.focusable(context, 16f)
            val content = if (isGlass) Glass.content else Neumorphic.content
            val contentMuted = if (isGlass) Glass.contentMuted else Neumorphic.contentMuted
            iconView.imageTintList = content
            title.setTextColor(content)
            subtitle.setTextColor(contentMuted)
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
