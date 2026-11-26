package com.eread.companion

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DataActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        val bookUrl = intent.getStringExtra("bookUrl").orEmpty()
        val bookName = intent.getStringExtra("bookName").orEmpty()
        var state = BookState.load(this, bookUrl, bookName)
        if (state.cards.isEmpty() && state.latestPos == 0) {
            BookState.all(this).firstOrNull { it.bookName == bookName }?.let { state = it }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.bg)
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "数据"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "返回"
            textSize = 13f
            setTextColor(Theme.accent)
            setBackground(rounded(0x1F7FB2FF, dp(10)))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { finish() }
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = bookName
            textSize = 13f
            setTextColor(Theme.sub)
            setPadding(0, dp(6), 0, dp(14))
        })

        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        body.addView(section("阅读位置"))
        body.addView(row("当前页", formatPos(state.currentPos)))
        body.addView(row("最新页", formatPos(state.latestPos)))

        body.addView(section("人物档案"))
        if (state.cards.isEmpty()) {
            body.addView(hint("还没有生成过人物概述"))
        } else {
            state.cards.values.sortedByDescending { it.updatedAt }.forEach { card ->
                body.addView(cardView(card, state))
            }
        }

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun cardView(card: Card, state: BookState): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.card, dp(12))
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = card.name
            textSize = 15f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = card.importance
            textSize = 10f
            setTextColor(Theme.accent)
            background = rounded(0x1F7FB2FF, dp(8))
            setPadding(dp(8), dp(3), dp(8), dp(3))
        })
        box.addView(top)
        box.addView(TextView(this).apply {
            text = formatPos(card.pos) + " · 更新 " + card.updates + " 次 · " + time(card.updatedAt)
            textSize = 10.5f
            setTextColor(Theme.faint)
            setPadding(0, dp(5), 0, dp(6))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        actions.addView(TextView(this).apply {
            text = "最新概述"
            textSize = 12f
            setTextColor(Theme.accent)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Theme.chip, dp(8))
            setOnClickListener {
                startActivity(android.content.Intent(this@DataActivity, SummaryActivity::class.java)
                    .putExtra("bookUrl", state.bookUrl)
                    .putExtra("bookName", state.bookName)
                    .putExtra("character", card.name))
            }
        })
        val lp2 = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp2.leftMargin = dp(8)
        actions.addView(TextView(this).apply {
            text = "详细档案"
            textSize = 12f
            setTextColor(Theme.accent)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(Theme.chip, dp(8))
            setOnClickListener {
                startActivity(android.content.Intent(this@DataActivity, BiographyActivity::class.java)
                    .putExtra("bookUrl", state.bookUrl)
                    .putExtra("bookName", state.bookName)
                    .putExtra("character", card.name))
            }
        }, lp2)
        actions.addView(LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        actions.addView(TextView(this).apply {
            text = "删除"
            textSize = 11f
            setTextColor(0xFFE57373.toInt())
            setPadding(dp(8), dp(8), dp(2), dp(8))
            setOnClickListener {
                state.cards.remove(card.name)
                state.save(this@DataActivity)
                recreate()
            }
        })
        box.addView(actions)
        return LinearLayout(this).apply {
            addView(box)
        }.also {
            (it.layoutParams) = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
    }

    private fun section(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(Theme.accent)
        setPadding(0, dp(16), 0, dp(6))
    }

    private fun hint(t: String) = TextView(this).apply {
        text = t
        textSize = 12f
        setTextColor(Theme.faint)
        setPadding(0, dp(4), 0, dp(8))
    }

    private fun row(k: String, v: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(4), 0, dp(4))
        addView(TextView(this@DataActivity).apply {
            text = k
            textSize = 13f
            setTextColor(Theme.sub)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(this@DataActivity).apply {
            text = v
            textSize = 13f
            setTextColor(Theme.text)
        })
    }

    private fun formatPos(pos: Int): String {
        if (pos <= 0) return "—"
        val chapter = Position.chapterOf(pos) + 1
        val offset = Position.posOf(pos)
        return "第 " + chapter + " 项 · 第 " + (offset / 330 + 1) + " 页（约）"
    }

    private fun time(t: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(t))

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }
}
