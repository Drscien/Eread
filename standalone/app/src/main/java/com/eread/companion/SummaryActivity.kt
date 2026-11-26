package com.eread.companion

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SummaryActivity : Activity() {
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        val bookUrl = intent.getStringExtra("bookUrl").orEmpty()
        val bookName = intent.getStringExtra("bookName").orEmpty()
        val name = intent.getStringExtra("character").orEmpty()
        var state = BookState.load(this, bookUrl, bookName)
        if (state.cards.isEmpty()) {
            BookState.all(this).firstOrNull { it.bookName == bookName }?.let { state = it }
        }
        val card = state.cards[name]

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.bg)
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = name
            textSize = 22f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "返回"
            textSize = 13f
            setTextColor(Theme.accent)
            background = rounded(0x1F7FB2FF, dp(10))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { finish() }
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = (card?.importance ?: Importance.DEFAULT) + " · 更新 " + (card?.updates ?: 0) + " 次" +
                (card?.updatedAt?.let { " · " + time(it) } ?: "")
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(6), 0, dp(10))
        })

        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = card?.summary?.takeIf { it.isNotBlank() } ?: "还没有最新概述"
            textSize = 15f
            setTextColor(Theme.textSoft)
            setLineSpacing(dp(6).toFloat(), 1f)
        })
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun time(t: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
        }
}
