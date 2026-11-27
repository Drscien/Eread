package com.eread.companion

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RelationActivity : Activity() {

    private lateinit var state: BookState
    private var character = ""

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.apply(this)
        val bookUrl = intent.getStringExtra("bookUrl").orEmpty()
        val bookName = intent.getStringExtra("bookName").orEmpty()
        character = intent.getStringExtra("character").orEmpty()
        state = BookState.load(this, bookUrl, bookName)
        if (state.cards.isEmpty()) {
            BookState.all(this).firstOrNull { it.bookName == bookName }?.let { state = it }
        }
        buildUi()
    }

    private fun buildUi() {
        val card = state.cards[character]
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
            text = character
            textSize = 22f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "复制"
            textSize = 13f
            setTextColor(Theme.accent)
            setBackground(rounded(0x1F7FB2FF, dp(10)))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                val text = state.cards[character]?.relations.orEmpty()
                if (text.isBlank()) {
                    android.widget.Toast.makeText(this@RelationActivity, "还没有关系列表", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val cm = getSystemService(android.content.ClipboardManager::class.java)
                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("Eread", text))
                    android.widget.Toast.makeText(this@RelationActivity, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })
        header.addView(TextView(this).apply {
            text = "返回"
            textSize = 13f
            setTextColor(Theme.accent)
            setBackground(rounded(0x1F7FB2FF, dp(10)))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { finish() }
        })
        root.addView(header)

        val ts = card?.relationsAt ?: 0L
        val latin = card?.latin.orEmpty()
        val sub = (if (latin.isNotBlank()) latin + "\n" else "") + if (ts > 0) {
            "人物关系 · 更新于 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
        } else {
            "人物关系"
        }
        root.addView(TextView(this).apply {
            text = sub
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(10), 0, dp(12))
        })

        val body = TextView(this).apply {
            text = card?.relations?.takeIf { it.isNotBlank() }
                ?: "还没有生成过关系列表。\n\n在悬浮窗的「关系网」里把此人设为主人物，选择次人物后点「生成关系列表」，结果会自动存到这里。"
            textSize = 14.5f
            setTextColor(Theme.textSoft)
            setLineSpacing(dp(6).toFloat(), 1f)
        }
        val scroll = ScrollView(this)
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }
}
