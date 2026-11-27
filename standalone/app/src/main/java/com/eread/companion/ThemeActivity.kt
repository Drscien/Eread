package com.eread.companion

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class ThemeActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.apply(this)
        val store = Store(this)

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
            text = "主题"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "返回"
            textSize = 13f
            setTextColor(Theme.accent)
            setPadding(dp(12), dp(6), dp(4), dp(6))
            setOnClickListener { finish() }
        })
        root.addView(header)

        root.addView(TextView(this).apply {
            text = "深色是默认外观；浅色跟随系统亮色风格。切换后立即生效。"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(8), 0, dp(16))
        })

        root.addView(option("深色", "深灰底 · 适合夜间阅读", true, store))
        val gap = LinearLayout.LayoutParams(0, dp(12))
        root.addView(android.view.View(this), gap)
        root.addView(option("浅色", "白底 · 接近系统浅色模式", false, store))

        setContentView(root)
    }

    private fun option(name: String, desc: String, dark: Boolean, store: Store): LinearLayout {
        val selected = (store.theme == "light") != dark
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawableCompat.rounded(
                if (selected) Theme.cardSel else Theme.card,
                dp(14),
                if (selected) dp(2) else dp(1),
                if (selected) Theme.accent else Theme.divider,
            )
            setOnClickListener {
                store.theme = if (dark) "dark" else "light"
                recreate()
            }
        }
        // 预览块
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawableCompat.rounded(
                if (dark) 0xFF11141A.toInt() else 0xFFF4F5F7.toInt(), dp(8), 0, 0
            )
        }
        preview.addView(android.view.View(this).apply {
            setBackgroundColor(if (dark) 0xFF191E26.toInt() else 0xFFFFFFFF.toInt())
        }, LinearLayout.LayoutParams(dp(52), dp(8)).apply { bottomMargin = dp(4) })
        preview.addView(android.view.View(this).apply {
            setBackgroundColor(if (dark) 0xFF7FB2FF.toInt() else 0xFF2F6FE0.toInt())
        }, LinearLayout.LayoutParams(dp(34), dp(6)))
        card.addView(preview, LinearLayout.LayoutParams(dp(68), dp(52)))

        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = name
            textSize = 15f
            setTextColor(Theme.text)
        })
        textCol.addView(TextView(this).apply {
            text = desc
            textSize = 11f
            setTextColor(Theme.sub)
            setPadding(0, dp(3), 0, 0)
        })
        val tlp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        tlp.leftMargin = dp(12)
        card.addView(textCol, tlp)

        card.addView(TextView(this).apply {
            text = if (selected) "●" else "○"
            textSize = 16f
            setTextColor(if (selected) Theme.accent else Theme.sub)
        })
        return card
    }
}
