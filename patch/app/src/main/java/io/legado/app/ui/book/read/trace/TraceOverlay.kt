package io.legado.app.ui.book.read.trace

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * 阅读页上的悬浮层：折叠态是一个圆球，展开态是搜索框 + 结果卡片。
 * 纯代码构建，不依赖布局文件和主题，方便直接贴进 legado。
 */
class TraceOverlay(
    private val activity: Activity,
    private val onSubmit: (String) -> Unit,
    private val onSettings: () -> Unit,
) {

    private val density = activity.resources.displayMetrics.density
    private fun dp(value: Int) = (value * density).toInt()

    private val root = FrameLayout(activity)
    private val ball = TextView(activity)
    private val panel = LinearLayout(activity)
    private val input = EditText(activity)
    private val search = TextView(activity)
    private val status = TextView(activity)
    private val body = TextView(activity)
    private val footer = TextView(activity)
    private val scroll = ScrollView(activity)

    init {
        build()
    }

    @SuppressLint("SetTextI18n")
    private fun build() {
        root.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.isClickable = false
        root.visibility = View.GONE

        ball.text = "人"
        ball.setTextColor(Color.WHITE)
        ball.textSize = 17f
        ball.gravity = Gravity.CENTER
        ball.background = circle(0xF01F2430.toInt())
        ball.elevation = dp(6).toFloat()
        ball.setOnClickListener { expand() }
        ball.setOnLongClickListener {
            onSettings()
            true
        }

        panel.orientation = LinearLayout.VERTICAL
        panel.background = rounded(0xF01B1F27.toInt(), dp(14))
        panel.setPadding(dp(14), dp(12), dp(14), dp(12))
        panel.elevation = dp(10).toFloat()
        panel.visibility = View.GONE

        val header = LinearLayout(activity)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        val title = TextView(activity)
        title.text = "人物速查"
        title.setTextColor(0xFFE8EAED.toInt())
        title.textSize = 15f

        val close = TextView(activity)
        close.text = "×"
        close.textSize = 22f
        close.setTextColor(0xFF9AA0A6.toInt())
        close.setPadding(dp(14), 0, dp(4), 0)
        close.setOnClickListener { collapse() }

        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(close)

        input.hint = "输入人物名，如：叶凡"
        input.textSize = 14f
        input.isSingleLine = true
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setTextColor(0xFFE8EAED.toInt())
        input.setHintTextColor(0xFF6B7280.toInt())
        input.imeOptions = EditorInfo.IME_ACTION_SEARCH
        input.setBackground(rounded(0xFF2A2F3A.toInt(), dp(8)))
        input.setPadding(dp(10), dp(8), dp(10), dp(8))
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submit()
                true
            } else {
                false
            }
        }

        search.text = "查"
        search.textSize = 15f
        search.setTextColor(Color.WHITE)
        search.gravity = Gravity.CENTER
        search.background = rounded(0xFF3B6FE0.toInt(), dp(8))
        search.setOnClickListener { submit() }

        val inputRow = LinearLayout(activity)
        inputRow.orientation = LinearLayout.HORIZONTAL
        inputRow.gravity = Gravity.CENTER_VERTICAL
        inputRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val searchLp = LinearLayout.LayoutParams(dp(56), dp(38))
        searchLp.leftMargin = dp(8)
        inputRow.addView(search, searchLp)

        status.textSize = 12f
        status.setTextColor(0xFF9AA0A6.toInt())

        body.textSize = 14f
        body.setTextColor(0xFFE8EAED.toInt())
        body.setLineSpacing(dp(4).toFloat(), 1f)

        footer.textSize = 11f
        footer.setTextColor(0xFF7F858D.toInt())

        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(body)

        panel.addView(header)
        val rowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rowLp.topMargin = dp(10)
        panel.addView(inputRow, rowLp)

        val statusLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        statusLp.topMargin = dp(10)
        panel.addView(status, statusLp)
        panel.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(250)))
        panel.addView(footer, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        val screenWidth = activity.resources.displayMetrics.widthPixels
        val panelWidth = minOf(dp(340), screenWidth - dp(24))
        val panelLp = FrameLayout.LayoutParams(panelWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        panelLp.gravity = Gravity.END or Gravity.TOP
        panelLp.topMargin = dp(88)
        panelLp.rightMargin = dp(8)
        panel.layoutParams = panelLp

        val ballLp = FrameLayout.LayoutParams(dp(50), dp(50))
        ballLp.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        ballLp.rightMargin = dp(6)
        ball.layoutParams = ballLp

        root.addView(panel)
        root.addView(ball)
    }

    fun attach() {
        if (root.parent != null) {
            root.visibility = View.VISIBLE
            return
        }
        val decor = activity.window.decorView as? ViewGroup ?: return
        decor.addView(root)
        root.visibility = View.VISIBLE
    }

    fun detach() {
        (root.parent as? ViewGroup)?.removeView(root)
    }

    fun hide() {
        root.visibility = View.GONE
    }

    private fun expand() {
        panel.visibility = View.VISIBLE
        ball.visibility = View.GONE
        input.requestFocus()
    }

    private fun collapse() {
        panel.visibility = View.GONE
        ball.visibility = View.VISIBLE
    }

    private fun submit() {
        val name = input.text?.toString()?.trim().orEmpty()
        if (name.length < 2) {
            status.text = "至少输入 2 个字"
            return
        }
        onSubmit(name)
    }

    @SuppressLint("SetTextI18n")
    fun render(state: TraceUiState) {
        when (state) {
            is TraceUiState.Idle -> {
                status.text = "输入人名后，会从当前页往回找最早出场"
                body.text = ""
                footer.text = ""
            }

            is TraceUiState.Scanning -> {
                status.text = state.message
                body.text = ""
                footer.text = ""
            }

            is TraceUiState.Summarizing -> {
                status.text = state.message
            }

            is TraceUiState.Failed -> {
                status.text = state.message
                footer.text = ""
            }

            is TraceUiState.Done -> {
                val result = state.result
                val first = if (result.firstChapterIndex >= 0) {
                    "最早出现：第 " + (result.firstChapterIndex + 1) + " 章 " + result.firstChapterTitle
                } else {
                    "最早出现：" + result.firstChapterTitle
                }
                status.text = first + " · 命中 " + result.hitCount + " 处"
                body.text = state.summary
                footer.text = buildString {
                    append("回扫 ").append(result.scannedChapters).append(" 章")
                    append("，有缓存 ").append(result.cachedChapters).append(" 章")
                    if (result.truncated) append("（已达扫描上限，结果可能不完整）")
                    if (state.fromCache) append(" · 来自缓存")
                }
            }
        }
    }

    private fun circle(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun rounded(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
        }
}
