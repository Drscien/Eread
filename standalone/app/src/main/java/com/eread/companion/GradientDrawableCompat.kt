package com.eread.companion

import android.graphics.drawable.GradientDrawable

object GradientDrawableCompat {
    fun rounded(color: Int, radius: Int, stroke: Int, strokeColor: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
            if (stroke > 0) setStroke(stroke, strokeColor)
        }
}
