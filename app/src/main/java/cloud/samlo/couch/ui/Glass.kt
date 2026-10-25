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
 * The sky layout's frosted glass: a faint white fill with a hairline edge, which turns solid
 * white with dark content when focused, as on Apple TV. Plain drawables, no blur, so it costs
 * nothing on weak GPUs.
 */
object Glass {
    const val FILL = 0x2EFFFFFF
    const val EDGE = 0x40FFFFFF
    const val FOCUSED = 0xF2FFFFFF.toInt()
    const val INK = 0xFF15171A.toInt()

    fun panel(context: Context, radiusDp: Float, fill: Int = FILL): Drawable = GradientDrawable().apply {
        cornerRadius = context.dpf(radiusDp)
        setColor(fill)
        setStroke(context.dp(1), EDGE)
    }

    /** Glass at rest, white when focused or pressed. */
    fun focusable(context: Context, radiusDp: Float): Drawable = StateListDrawable().apply {
        val focused = GradientDrawable().apply {
            cornerRadius = context.dpf(radiusDp)
            setColor(FOCUSED)
        }
        addState(intArrayOf(android.R.attr.state_focused), focused)
        addState(intArrayOf(android.R.attr.state_pressed), focused)
        addState(intArrayOf(), panel(context, radiusDp))
    }

    /** White content at rest, dark ink on the white focused glass. */
    val content: ColorStateList
        get() = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(INK, INK, Tokens.textPrimary),
        )

    val contentMuted: ColorStateList
        get() = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(0xB315171A.toInt(), 0xB315171A.toInt(), 0xB3EDEDEF.toInt()),
        )

    /** A rounded pill with an optional icon and a label: inputs and other top-bar actions. */
    fun pill(context: Context, iconRes: Int, label: String?, onClick: () -> Unit) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isFocusable = true
        isClickable = true
        isDuplicateParentStateEnabled = false
        background = focusable(context, 20f)
        val side = context.dp(if (label == null) 0 else 14)
        setPadding(if (label == null) 0 else context.dp(12), 0, side, 0)
        if (label == null) gravity = Gravity.CENTER
        if (iconRes != 0) addView(icon(context, iconRes, 18), LinearLayout.LayoutParams(context.dp(18), context.dp(18)).apply {
            if (label != null) marginEnd = context.dp(7)
        })
        if (label != null) addView(TextView(context).apply {
            text = label
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(content)
            isDuplicateParentStateEnabled = true
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        contentDescription = label
        setOnClickListener { onClick() }
    }

    fun icon(context: Context, res: Int, sizeDp: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = content
        isDuplicateParentStateEnabled = true
        scaleType = ImageView.ScaleType.FIT_CENTER
        minimumWidth = context.dp(sizeDp)
    }
}
