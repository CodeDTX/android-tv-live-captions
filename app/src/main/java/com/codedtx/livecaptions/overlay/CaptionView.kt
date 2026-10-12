package com.codedtx.livecaptions.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.accessibility.CaptioningManager.CaptionStyle
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.codedtx.livecaptions.caption.CaptionFrame

/**
 * The caption block. Its look comes from the TV's own caption settings
 * (Settings, Accessibility, Captions), so someone who has already chosen a size
 * and colour for subtitles gets the same choice here.
 *
 * Words the recogniser may still change are drawn at reduced opacity, then
 * settle to full strength once confirmed. That is the only styling this app adds.
 */
class CaptionView(context: Context) : TextView(context) {

    private var foreground = Color.WHITE

    init {
        gravity = Gravity.CENTER
        includeFontPadding = false
        visibility = View.GONE
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun applyStyle(style: CaptionStyle, fontScale: Float, screenWidth: Int, screenHeight: Int) {
        foreground = if (style.hasForegroundColor()) style.foregroundColor else Color.WHITE
        val background = if (style.hasBackgroundColor()) style.backgroundColor else DEFAULT_BACKGROUND

        // One caption line is about a fifteenth of the picture height.
        val size = screenHeight * TEXT_HEIGHT * fontScale
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        setLineSpacing(size * 0.12f, 1f)
        typeface = style.typeface ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        maxWidth = (screenWidth * MAX_WIDTH).toInt()

        val padH = (size * 0.45f).toInt()
        val padV = (size * 0.18f).toInt()
        setPadding(padH, padV, padH, padV)
        this.background = GradientDrawable().apply {
            setColor(background)
            cornerRadius = size * 0.12f
        }

        if (style.hasEdgeType() && style.edgeType == CaptionStyle.EDGE_TYPE_DROP_SHADOW) {
            val edge = if (style.hasEdgeColor()) style.edgeColor else Color.BLACK
            setShadowLayer(size * 0.08f, size * 0.04f, size * 0.04f, edge)
        } else {
            setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
        }
        setTextColor(foreground)
    }

    fun show(frame: CaptionFrame) {
        if (frame.isEmpty) {
            visibility = View.GONE
            return
        }
        val tentative = ColorUtils.setAlphaComponent(foreground, (Color.alpha(foreground) * TENTATIVE_ALPHA).toInt())
        val text = SpannableStringBuilder()
        frame.lines.forEachIndexed { index, line ->
            if (index > 0) text.append('\n')
            line.forEachIndexed { i, word ->
                if (i > 0) text.append(' ')
                val start = text.length
                text.append(word.text)
                if (!word.confirmed) {
                    text.setSpan(ForegroundColorSpan(tentative), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }
        setText(text)
        visibility = View.VISIBLE
    }

    private companion object {
        const val TEXT_HEIGHT = 0.053f
        const val MAX_WIDTH = 0.8f
        const val TENTATIVE_ALPHA = 0.7f
        val DEFAULT_BACKGROUND = Color.argb(204, 0, 0, 0)
    }
}
