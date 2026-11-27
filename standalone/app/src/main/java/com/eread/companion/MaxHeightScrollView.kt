package com.eread.companion

import android.content.Context
import android.view.View
import android.widget.ScrollView

/** 高度随内容增长，超过上限才滚动的 ScrollView。 */
class MaxHeightScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, capped)
    }
}
