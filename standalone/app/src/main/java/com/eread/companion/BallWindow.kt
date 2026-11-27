package com.eread.companion

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/** 悬浮球：拖动、贴边、点击展开、长按呼出「设置 / 停止」。 */
class BallWindow(
    private val service: Service,
    private val store: Store,
    private val onTap: () -> Unit,
    private val onSettings: () -> Unit,
    private val onStop: () -> Unit,
) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val d = service.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private var ball: View? = null
    private var ballLp: WindowManager.LayoutParams? = null
    private var radialOpen = false
    private val radialViews = ArrayList<View>()
    private val radialOffsets = HashMap<View, Pair<Float, Float>>()
    private var radialScrim: View? = null
    private val radialAutoHide = Runnable { hideRadial() }

    fun showing(): Boolean = ball != null

    fun show() {
        if (ball != null) return
        val size = dp(36)
        val metrics = service.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val v = BallView(service)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (store.ballX >= 0f) (store.ballX * screenW).toInt().coerceIn(0, screenW - size)
            else screenW - size - dp(8)
            y = if (store.ballY >= 0f) (store.ballY * screenH).toInt().coerceIn(0, screenH - size)
            else screenH / 2 - size / 2
        }
        ballLp = lp
        attachTouch(v, lp)
        runCatching { wm.addView(v, lp) }
        ball = v
    }

    fun hide() {
        hideRadial()
        ball?.let { runCatching { wm.removeView(it) } }
        ball = null
    }

    private fun attachTouch(v: View, lp: WindowManager.LayoutParams) {
        val slop = dp(6)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longFired = false
        val longPress = Runnable {
            longFired = true
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showRadial()
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longFired = false
                    main.postDelayed(longPress, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        main.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    if (!dragging && !longFired) {
                        if (radialOpen) hideRadial() else onTap()
                    }
                    if (dragging) {
                        val m = service.resources.displayMetrics
                        store.ballX = lp.x.toFloat() / m.widthPixels
                        store.ballY = lp.y.toFloat() / m.heightPixels
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun showRadial() {
        if (radialOpen) {
            hideRadial()
            return
        }
        val lp = ballLp ?: return
        radialOpen = true
        val m = service.resources.displayMetrics
        val s = lp.width
        val cx = lp.x + s / 2f
        val cy = lp.y + s / 2f
        val radius = dp(67)
        val base = if (cx < m.widthPixels / 2f) 0.0 else 180.0
        val bubble = dp(42)

        val scrim = View(service).apply {
            alpha = 0f
            setOnClickListener { hideRadial() }
        }
        val scrimLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(scrim, scrimLp) }
        radialScrim = scrim

        val items = listOf(
            Triple("设置", 0, R.drawable.ic_settings),
            Triple("停止", 1, R.drawable.ic_close_red),
        )
        items.forEach { (_, index, iconRes) ->
            val angle = Math.toRadians(base + if (index == 0) -45.0 else 45.0)
            val bx = (cx + radius * Math.cos(angle)).toInt() - bubble / 2
            val by = (cy + radius * Math.sin(angle)).toInt() - bubble / 2
            val holder = FrameLayout(service).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x661F2430)
                }
                alpha = 0f
            }
            holder.addView(ImageView(service).apply {
                setImageResource(iconRes)
                if (index == 0) setColorFilter(0xE6E8EAED.toInt())
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            holder.setOnClickListener {
                hideRadial()
                if (index == 0) onSettings() else onStop()
            }
            val itemLp = WindowManager.LayoutParams(
                bubble, bubble,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bx.coerceIn(0, m.widthPixels - bubble)
                y = by.coerceIn(0, m.heightPixels - bubble)
            }
            val dx = cx - (itemLp.x + bubble / 2f)
            val dy = cy - (itemLp.y + bubble / 2f)
            holder.translationX = dx
            holder.translationY = dy
            holder.scaleX = 0.55f
            holder.scaleY = 0.55f
            radialOffsets[holder] = dx to dy
            runCatching { wm.addView(holder, itemLp) }
            radialViews.add(holder)
            holder.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f)
                .alpha(1f)
                .setDuration(210)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        main.postDelayed(radialAutoHide, 5000)
    }

    fun hideRadial() {
        main.removeCallbacks(radialAutoHide)
        if (!radialOpen) return
        radialOpen = false
        val list = radialViews.toList()
        radialViews.clear()
        list.forEach { v ->
            val off = radialOffsets.remove(v) ?: (0f to 0f)
            v.isClickable = false
            v.animate()
                .translationX(off.first).translationY(off.second)
                .scaleX(0.55f).scaleY(0.55f)
                .alpha(0f)
                .setDuration(170)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }
        val scrim = radialScrim
        radialScrim = null
        main.postDelayed({
            list.forEach { runCatching { wm.removeView(it) } }
            scrim?.let { runCatching { wm.removeView(it) } }
        }, 190)
    }
}
