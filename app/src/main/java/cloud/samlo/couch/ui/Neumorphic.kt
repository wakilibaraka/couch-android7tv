package cloud.samlo.couch.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Modern Neumorphic surface system: soft, calm, tactile surfaces with high contrast text
 * designed for effortless readability from 3 meters on the sofa.
 * Built entirely with static GradientDrawables for 0% CPU/GPU overhead on Android 7.
 */
object Neumorphic {
    const val BASE_TOP = 0xF0282E3D.toInt()
    const val BASE_BOTTOM = 0xF51A1E28.toInt()
    const val EDGE_LIGHT = 0x24FFFFFF
    const val FOCUS_FILL = 0xF5333B4E.toInt()
    const val FOCUS_RING = 0xFF60A5FA.toInt()

    fun panel(context: Context, radiusDp: Float): Drawable = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(BASE_TOP, BASE_BOTTOM),
    ).apply {
        cornerRadius = context.dpf(radiusDp)
        setStroke(context.dp(1), EDGE_LIGHT)
    }

    /** Neumorphic at rest, glowing accent ring and elevated fill when focused. */
    fun focusable(context: Context, radiusDp: Float): Drawable = StateListDrawable().apply {
        val focused = GradientDrawable().apply {
            cornerRadius = context.dpf(radiusDp)
            setColor(FOCUS_FILL)
            setStroke(context.dp(2), Tokens.accent)
        }
        addState(intArrayOf(android.R.attr.state_focused), focused)
        addState(intArrayOf(android.R.attr.state_pressed), focused)
        addState(intArrayOf(), panel(context, radiusDp))
    }

    val content: ColorStateList
        get() = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFF1F5F9.toInt()),
        )

    val contentMuted: ColorStateList
        get() = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(0xFF93C5FD.toInt(), 0xFF93C5FD.toInt(), 0xFF94A3B8.toInt()),
        )

    /** Pill with optional icon and label. */
    fun pill(context: Context, iconRes: Int, label: String?, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        background = focusable(context, 12f)
        val side = context.dp(if (label == null) 0 else 14)
        setPadding(if (label == null) 0 else context.dp(12), 0, side, 0)
        if (iconRes != 0) addView(icon(context, iconRes, 18), LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
            if (label != null) marginEnd = context.dp(8)
        })
        if (label != null) addView(TextView(context).apply {
            text = label
            textSize = 13f
            typeface = Fonts.hanken(context, 600)
            setTextColor(content)
            isDuplicateParentStateEnabled = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        setOnClickListener { onClick() }
    }

    fun icon(context: Context, resId: Int, sizeDp: Int) = ImageView(context).apply {
        setImageResource(resId)
        scaleType = ImageView.ScaleType.FIT_CENTER
        imageTintList = content
        isDuplicateParentStateEnabled = true
    }
}
