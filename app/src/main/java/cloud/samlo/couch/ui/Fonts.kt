package cloud.samlo.couch.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build

/**
 * Hanken Grotesk, the Index layout's typeface: one variable font (Latin subset, OFL) in
 * assets, instanced per weight on first use. Glyphs it lacks fall back to the system font.
 */
object Fonts {
    private const val PATH = "fonts/hanken_grotesk.ttf"
    private val cache = HashMap<Int, Typeface>()
    private var clockFace: Typeface? = null

    /** Righteous (OFL), the sky layout's clock face; the system font if the asset can't load. */
    fun clock(context: Context): Typeface = clockFace ?: (try {
        Typeface.createFromAsset(context.assets, "fonts/righteous.ttf")
    } catch (e: Exception) {
        Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }).also { clockFace = it }

    /** [weight] is a CSS-style weight: 400 regular, 500 medium, 600 semibold, 700 bold. */
    fun hanken(context: Context, weight: Int): Typeface = cache.getOrPut(weight) {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                Typeface.Builder(context.assets, PATH).setFontVariationSettings("'wght' $weight").setWeight(weight).build()
            } catch (e: Exception) {
                null
            } ?: Typeface.create(Typeface.SANS_SERIF, weight, false)
        } else {
            // Android 7 can't pick a variable font's instance: the default one, made bold from 600 up.
            val style = if (weight >= 600) Typeface.BOLD else Typeface.NORMAL
            try {
                Typeface.create(Typeface.createFromAsset(context.assets, PATH), style)
            } catch (e: Exception) {
                Typeface.create(Typeface.SANS_SERIF, style)
            }
        }
    }
}
