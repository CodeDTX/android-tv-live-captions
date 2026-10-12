package com.codedtx.livecaptions.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.CaptioningManager
import android.widget.FrameLayout
import com.codedtx.livecaptions.caption.CaptionBus
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Draws the captions on top of every other app.
 *
 * An accessibility service, rather than the "display over other apps"
 * permission, for two reasons. Several Android TV builds have no settings screen
 * to grant that permission at all, and an accessibility overlay is what Android
 * intends for exactly this job. The service reads no screen content and handles
 * no events; it exists only to own the overlay window.
 */
class CaptionOverlayService : AccessibilityService() {

    private val scope = MainScope()
    private var root: FrameLayout? = null
    private var view: CaptionView? = null
    private var captioning: CaptioningManager? = null

    private val styleListener = object : CaptioningManager.CaptioningChangeListener() {
        override fun onUserStyleChanged(userStyle: CaptioningManager.CaptionStyle) = applyStyle()
        override fun onFontScaleChanged(fontScale: Float) = applyStyle()
    }

    override fun onServiceConnected() {
        val windowManager = getSystemService(WindowManager::class.java)
        val view = CaptionView(this)
        // A full-width window with the caption centred inside it. A window that
        // wraps its content keeps the width of its first layout, which narrowed
        // the block and broke lines the caption track had already wrapped.
        val root = FrameLayout(this).apply {
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL,
                ),
            )
        }
        // Display metrics rather than window metrics: the latter need Android 11.
        val screenHeight = resources.displayMetrics.heightPixels

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            // Clear of the 5% title-safe margin, where broadcast captions sit.
            y = (screenHeight * BOTTOM_MARGIN).toInt()
        }
        windowManager.addView(root, params)
        this.root = root
        this.view = view

        captioning = getSystemService(CaptioningManager::class.java).also {
            it.addCaptioningChangeListener(styleListener)
        }
        applyStyle()

        scope.launch { CaptionBus.frame.collect { view.show(it) } }
    }

    private fun applyStyle() {
        val view = view ?: return
        val captioning = captioning ?: return
        val metrics = resources.displayMetrics
        view.applyStyle(
            style = captioning.userStyle,
            fontScale = captioning.fontScale,
            screenWidth = metrics.widthPixels,
            screenHeight = metrics.heightPixels,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        captioning?.removeCaptioningChangeListener(styleListener)
        root?.let { getSystemService(WindowManager::class.java).removeView(it) }
        root = null
        view = null
        super.onDestroy()
    }

    private companion object {
        const val BOTTOM_MARGIN = 0.08
    }
}
