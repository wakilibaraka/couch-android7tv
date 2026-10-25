package cloud.samlo.couch.ui

import android.content.Context

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

fun Context.dpf(value: Float): Float = value * resources.displayMetrics.density

/**
 * The UI is drawn on a 960x540dp canvas. TVs that report another density (the Vision TV says
 * 240dpi at 1080p, which makes 1280x720dp) get a context with the density that fits it, so
 * every screen keeps its proportions. Use from an activity's `attachBaseContext`.
 */
fun canvasContext(base: Context): Context {
    val metrics = base.resources.displayMetrics
    val wide = maxOf(metrics.widthPixels, metrics.heightPixels)
    if (wide <= 0) return base
    val dpi = wide * android.util.DisplayMetrics.DENSITY_DEFAULT / CANVAS_W
    val current = base.resources.configuration
    if (current.densityDpi == dpi) return base
    val config = android.content.res.Configuration(current).apply { densityDpi = dpi }
    return base.createConfigurationContext(config)
}

private const val CANVAS_W = 960
