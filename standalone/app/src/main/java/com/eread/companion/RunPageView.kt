package com.eread.companion

import android.app.ActivityManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** 运行页：来源切换、启动/停止、导入入口、上次查询状态。 */
class RunPageView(
    context: android.content.Context,
    private val store: Store,
    private val localStore: LocalStore,
    private val onToggleOverlay: () -> Unit,
    private val onImport: () -> Unit,
    private val onHelp: () -> Unit,
) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val modeHttp = LinearLayout(context)
    private val modeLocal = LinearLayout(context)
    private val statusText = TextView(context)
    private val startButton = Button(context)
    private val importHint = TextView(context)

    init {
        orientation = VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(16))
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        build()
        refresh()
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun build() {
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(context).apply {
            text = "人物速查"
            textSize = 24f
            setTextColor(Theme.text)
        }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "?"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.sub)
            background = rounded(Theme.cardAlt, dp(11))
            setOnClickListener { onHelp() }
        }, LayoutParams(dp(22), dp(22)))
        addView(header)

        addView(TextView(context).apply {
            text = "读取方式"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(14), 0, dp(6))
        })

        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        modeCard(modeHttp, "Legado 模式", "接口自动定位 · 片段可跳转")
        modeCard(modeLocal, "Readest 模式", "OCR / 手动定位 · 本地全文")
        modeHttp.setOnClickListener {
            store.source = "legado"
            refresh()
        }
        modeLocal.setOnClickListener {
            store.source = "readest"
            refresh()
        }
        val lp1 = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.rightMargin = dp(8)
        row.addView(modeHttp, lp1)
        row.addView(modeLocal, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(row)

        statusText.textSize = 13f
        statusText.setTextColor(Theme.sub)
        statusText.setPadding(0, dp(16), 0, 0)
        addView(statusText)

        startButton.text = "启动悬浮窗"
        startButton.textSize = 15f
        startButton.setTextColor(Color.WHITE)
        startButton.background = rounded(Theme.accentStrong, dp(12))
        startButton.setOnClickListener { onToggleOverlay() }
        addView(startButton, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(20)
        })

        addView(Button(context).apply {
            text = "导入本地文件"
            textSize = 14f
            setTextColor(Theme.text)
            background = rounded(Theme.cardAlt, dp(12))
            setOnClickListener { onImport() }
        }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })

        importHint.textSize = 12f
        importHint.setTextColor(Theme.faint)
        importHint.setPadding(0, dp(10), 0, 0)
        addView(importHint)

        addView(TextView(context).apply {
            text = "悬浮球可拖动；长按球呼出「设置 / 停止」两个菜单球。查询结果按已读位置截断，不会剧透后文。\n\n" +
                "Readest 等没有本地接口的阅读器：把同一本 EPUB 导入 Eread，在面板的「页码校准」里选择章节，即可不依赖 legado。"
            textSize = 12f
            setTextColor(Theme.faint)
            setPadding(0, dp(14), 0, 0)
        })
    }

    private fun modeCard(card: LinearLayout, title: String, subtitle: String) {
        card.orientation = VERTICAL
        card.setPadding(dp(12), dp(12), dp(12), dp(12))
        card.background = rounded(Theme.card, dp(12))
        card.addView(TextView(context).apply {
            text = title
            textSize = 14f
            setTextColor(Theme.text)
        })
        card.addView(TextView(context).apply {
            text = subtitle
            textSize = 11f
            setTextColor(Theme.sub)
            setPadding(0, dp(4), 0, 0)
        })
    }

    fun refresh() {
        val legado = store.source != "readest"
        modeHttp.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (legado) Theme.cardSel else Theme.card)
            if (legado) setStroke(dp(1), Theme.accent)
        }
        modeLocal.background = GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (legado) Theme.card else Theme.cardSel)
            if (!legado) setStroke(dp(1), 0xFF3FA46A.toInt())
        }
        val local = store.lastMode == "local"
        importHint.text = "已导入 " + localStore.books().size + " 本本地书；查询会自动优先使用本地全文。"
        val book = store.lastBook
        val chapter = store.lastChapter
        statusText.text = if (book.isBlank()) {
            "还没有查询记录。打开一本书，点悬浮球输入人名开始。"
        } else {
            "上次查询：《" + book + "》" + (if (chapter >= 0) " 第 " + (chapter + 1) + " 项" else "") +
                "\n当前命中方式：" + (if (local) "本地文件" else "legado 接口")
        }
        val running = isServiceRunning()
        startButton.text = if (running) "停止悬浮窗" else "启动悬浮窗"
        startButton.background = rounded(if (running) 0xFFC0453F.toInt() else Theme.accentStrong, dp(12))
    }

    fun refreshLater(delayMs: Long) {
        postDelayed({ refresh() }, delayMs)
    }

    private fun isServiceRunning(): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == OverlayService::class.java.name }
    }
}
