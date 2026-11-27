package com.eread.companion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** 灰色半透明放大镜悬浮球 */
class BallView(context: Context) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x661F2430
    }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        color = 0xCCD5D9DE.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val r = w / 2f
        canvas.drawCircle(r, r, r, fill)

        val cx = w * 0.46f
        val cy = w * 0.46f
        val rad = w * 0.16f
        canvas.drawCircle(cx, cy, rad, stroke)

        val k = rad * 0.7071f
        val x1 = cx + k
        val y1 = cy + k
        val x2 = w * 0.75f
        val y2 = w * 0.75f
        canvas.drawLine(x1, y1, x2, y2, stroke)
    }
}
