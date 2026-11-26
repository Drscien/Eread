package com.eread.companion

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class PanelView(
    context: Context,
    private val onSubmit: (String) -> Unit,
    private val onClose: () -> Unit,
    private val onJump: (TraceHit) -> Unit,
    private val pageOfHit: (Int, Int) -> Int,
    private val onRegenerate: () -> Unit,
    private val onSetLatest: () -> Unit,
    private val onAliasMerge: () -> Unit,
    private val onCalibrate: (Int) -> Unit,
    private val onIdentify: () -> Unit,
) : LinearLayout(context) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    val headerView: LinearLayout = LinearLayout(context)
    private val tabSearch = TextView(context)
    private val tabIdentify = TextView(context)
    private val tabCalib = TextView(context)
    private val searchPane = LinearLayout(context)
    private val identifyPane = LinearLayout(context)
    private val calibPane = LinearLayout(context)

    private val bookLabel = TextView(context)
    private val calibBookLabel = TextView(context)
    private val input = EditText(context)
    private val status = TextView(context)
    private val summaryBox = LinearLayout(context)
    private val summaryScroll = ScrollView(context)
    private val summaryText = TextView(context)
    private val summaryLabel = TextView(context)
    private val sectionLabel = TextView(context)
    private val sectionDivider = View(context)
    private val actionRow = LinearLayout(context)
    private val list = LinearLayout(context)
    private val footer = TextView(context)
    private val scroll = ScrollView(context)

    private val calibInput = EditText(context)
    private val calibStatus = TextView(context)

    private val identifyButton = TextView(context)
    private val identifyStatus = TextView(context)
    private val identifyList = LinearLayout(context)

    private var mode = 0

    init {
        orientation = VERTICAL
        background = rounded(Theme.panel, dp(14))
        setPadding(dp(14), dp(12), dp(14), dp(12))
        build()
    }

    private fun build() {
        val header = headerView
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL

        tabSearch.text = "人物速查"
        tabIdentify.text = "快速识别"
        tabCalib.text = "页码校准"
        tabSearch.setOnClickListener { switchTab(0) }
        tabIdentify.setOnClickListener { switchTab(1) }
        tabCalib.setOnClickListener { switchTab(2) }

        val close = TextView(context).apply {
            text = "×"
            textSize = 22f
            setTextColor(Theme.sub)
            setPadding(dp(14), 0, dp(4), 0)
            setOnClickListener { onClose() }
        }
        header.addView(tabSearch)
        header.addView(tabIdentify)
        header.addView(tabCalib)
        header.addView(View(context), LayoutParams(0, 1, 1f))
        header.addView(close)

        // ---------- search ----------
        searchPane.orientation = VERTICAL
        bookLabel.textSize = 11f
        bookLabel.setTextColor(Theme.sub)
        bookLabel.setPadding(dp(2), dp(4), 0, 0)
        searchPane.addView(bookLabel)

        input.hint = "输入人物名，如：叶凡"
        input.textSize = 14f
        input.isSingleLine = true
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setTextColor(Theme.text)
        input.setHintTextColor(Theme.faint)
        input.setPadding(dp(10), dp(8), dp(10), dp(8))
        input.setBackground(rounded(Theme.field, dp(8)))
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEARCH) { submit(); true } else false
        }
        val search = FrameLayout(context).apply {
            background = rounded(Theme.accentStrong, dp(8))
            setOnClickListener { submit() }
        }
        search.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
        }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(input, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val slp = LayoutParams(dp(48), dp(38))
        slp.leftMargin = dp(8)
        row.addView(search, slp)
        val rlp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rlp.topMargin = dp(10)
        searchPane.addView(row, rlp)

        status.textSize = 12f
        status.setTextColor(Theme.sub)
        val stlp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        stlp.topMargin = dp(10)
        searchPane.addView(status, stlp)

        summaryBox.orientation = HORIZONTAL
        summaryBox.setPadding(dp(10), dp(10), dp(12), dp(10))
        summaryBox.background = rounded(Theme.cardSel, dp(12))
        summaryBox.visibility = GONE
        val accentBar = View(context).apply { setBackgroundColor(Theme.accent) }
        val barLp = LayoutParams(dp(3), ViewGroup.LayoutParams.MATCH_PARENT)
        barLp.rightMargin = dp(10)
        summaryBox.addView(accentBar, barLp)
        val summaryInner = LinearLayout(context).apply { orientation = VERTICAL }
        summaryLabel.text = "摘要 · AI 生成"
        summaryLabel.textSize = 10f
        summaryLabel.setTextColor(Theme.accent)
        summaryScroll.isVerticalScrollBarEnabled = false
        summaryScroll.addView(summaryText)
        summaryText.textSize = 14f
        summaryText.setTextColor(Theme.textSoft)
        summaryText.setLineSpacing(dp(5).toFloat(), 1f)
        summaryText.setPadding(0, dp(6), 0, dp(6))
        summaryInner.addView(summaryLabel)
        summaryInner.addView(summaryScroll)

        actionRow.orientation = HORIZONTAL
        actionRow.gravity = Gravity.END
        actionRow.visibility = GONE
        actionRow.addView(TextView(context).apply {
            text = "重新生成"
            textSize = 11f
            setTextColor(Theme.sub)
            setPadding(dp(10), dp(8), dp(10), dp(6))
            setOnClickListener { onRegenerate() }
        })
        actionRow.addView(TextView(context).apply {
            text = "设为最新页"
            textSize = 11f
            setTextColor(Theme.sub)
            setPadding(dp(10), dp(8), dp(4), dp(6))
            setOnClickListener { onSetLatest() }
        })
        summaryInner.addView(actionRow)
        summaryBox.addView(summaryInner, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val smlp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        smlp.topMargin = dp(10)
        searchPane.addView(summaryBox, smlp)

        sectionLabel.text = "原文片段 · 点击可跳转"
        sectionLabel.textSize = 10f
        sectionLabel.setTextColor(Theme.faint)
        sectionLabel.visibility = GONE
        sectionLabel.setPadding(dp(2), 0, 0, dp(2))
        sectionDivider.setBackgroundColor(Theme.divider)
        val sdLp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        sdLp.topMargin = dp(14)
        searchPane.addView(sectionDivider, sdLp)
        val sclp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        sclp.topMargin = dp(8)
        searchPane.addView(sectionLabel, sclp)

        list.orientation = VERTICAL
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        searchPane.addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(230)))
        footer.textSize = 11f
        footer.setTextColor(Theme.faint)
        searchPane.addView(footer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---------- identify ----------
        identifyPane.orientation = VERTICAL
        identifyPane.visibility = GONE
        identifyButton.text = "快速识别人名"
        identifyButton.textSize = 15f
        identifyButton.gravity = Gravity.CENTER
        identifyButton.setTextColor(Color.WHITE)
        identifyButton.background = rounded(Theme.accentStrong, dp(12))
        identifyButton.setPadding(0, dp(14), 0, dp(14))
        identifyButton.setOnClickListener {
            if (identifyButton.isEnabled) {
                markIdentifying()
                onIdentify()
            }
        }
        identifyPane.addView(identifyButton, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        identifyStatus.textSize = 11f
        identifyStatus.setTextColor(Theme.sub)
        identifyStatus.setPadding(dp(2), dp(10), 0, dp(4))
        identifyPane.addView(identifyStatus)
        identifyList.orientation = VERTICAL
        identifyPane.addView(identifyList, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // ---------- calibrate ----------
        calibPane.orientation = VERTICAL
        calibPane.visibility = GONE
        calibBookLabel.textSize = 11f
        calibBookLabel.setTextColor(Theme.sub)
        calibBookLabel.setPadding(dp(2), dp(4), 0, 0)
        calibPane.addView(calibBookLabel)
        calibPane.addView(TextView(context).apply {
            text = "打开阅读器当前这一章，填入底部显示的页码。\n校准后，同一章的片段页码会用它做插值。"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(10), 0, dp(10))
        })
        val calibRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        calibInput.hint = "例如 12"
        calibInput.textSize = 14f
        calibInput.inputType = InputType.TYPE_CLASS_NUMBER
        calibInput.setTextColor(Theme.text)
        calibInput.setHintTextColor(Theme.faint)
        calibInput.setPadding(dp(10), dp(8), dp(10), dp(8))
        calibInput.setBackground(rounded(Theme.field, dp(8)))
        calibRow.addView(calibInput, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        calibRow.addView(TextView(context).apply {
            text = "校准"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Theme.accentStrong, dp(8))
            setOnClickListener {
                val page = calibInput.text?.toString()?.trim()?.toIntOrNull()
                if (page == null || page <= 1) {
                    calibStatus.text = "页码要大于 1"
                } else {
                    hideKeyboard()
                    calibStatus.text = "校准中…"
                    onCalibrate(page)
                }
            }
        }, LayoutParams(dp(64), dp(38)).apply { leftMargin = dp(8) })
        calibPane.addView(calibRow)

        calibStatus.textSize = 12f
        calibStatus.setTextColor(Theme.accent)
        calibStatus.setPadding(0, dp(10), 0, 0)
        calibPane.addView(calibStatus)

        addView(header)
        addView(searchPane)
        addView(identifyPane)
        addView(calibPane)

        switchTab(0)
    }

    private fun switchTab(index: Int) {
        mode = index
        searchPane.visibility = if (index == 0) VISIBLE else GONE
        identifyPane.visibility = if (index == 1) VISIBLE else GONE
        calibPane.visibility = if (index == 2) VISIBLE else GONE
        listOf(tabSearch, tabIdentify, tabCalib).forEachIndexed { i, tv ->
            val active = i == index
            tv.textSize = if (active) 15f else 12.5f
            tv.setTextColor(if (active) Theme.text else Theme.sub)
            tv.setPadding(dp(2), dp(2), dp(12), dp(4))
        }
        if (index == 0) focusInput()
    }

    fun markIdentifying() {
        identifyButton.isEnabled = false
        identifyButton.text = "识别中…"
        identifyButton.background = rounded(0xFF2A3140.toInt(), dp(12))
        identifyStatus.text = "正在读取当前页…"
        identifyList.removeAllViews()
    }

    fun showNames(names: List<String>) {
        identifyButton.isEnabled = true
        identifyButton.text = "快速识别人名"
        identifyButton.background = rounded(Theme.accentStrong, dp(12))
        identifyList.removeAllViews()
        if (names.isEmpty()) {
            identifyStatus.text = "这一页没识别到人名，换个位置再试"
            return
        }
        identifyStatus.text = "点名字直接去查："
        var rowView: LinearLayout? = null
        names.forEachIndexed { i, name ->
            if (i % 2 == 0) {
                rowView = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    setPadding(0, dp(4), 0, 0)
                }
                identifyList.addView(rowView)
            }
            val chip = TextView(context).apply {
                text = name
                textSize = 13f
                setTextColor(0xFF9CC0FF.toInt())
                gravity = Gravity.CENTER
                background = rounded(Theme.chip, dp(9))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                setOnClickListener {
                    input.setText(name)
                    switchTab(0)
                    onSubmit(name)
                }
            }
            val lp = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = dp(8)
            rowView?.addView(chip, lp)
        }
    }

    fun showIdentifyError(msg: String) {
        identifyButton.isEnabled = true
        identifyButton.text = "快速识别人名"
        identifyButton.background = rounded(Theme.accentStrong, dp(12))
        identifyStatus.text = msg
    }

    fun setQuery(text: String) {
        input.setText(text)
        input.setSelection(input.text?.length ?: 0)
    }

    fun focusInput() = input.requestFocus()

    fun setBookLabel(text: String) {
        bookLabel.text = text
        calibBookLabel.text = text
    }

    fun showCalibrationResult(text: String) {
        calibStatus.text = text
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
        calibInput.clearFocus()
    }

    private fun submit() {
        val name = input.text?.toString()?.trim().orEmpty()
        if (name.length < 2) {
            status.text = "至少输入 2 个字"
            return
        }
        val imm = context.getSystemService(InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(windowToken, 0)
        input.clearFocus()
        onSubmit(name)
    }

    @SuppressLint("SetTextI18n")
    fun render(state: UiState) {
        if (mode != 0) return
        when (state) {
            is UiState.Idle -> {
                status.text = "输入人名，从当前页往回找最早出场"
                summaryBox.visibility = GONE
                sectionLabel.visibility = GONE
                sectionDivider.visibility = GONE
                list.removeAllViews()
                footer.text = ""
                scroll.visibility = GONE
            }
            is UiState.Busy -> status.text = state.message
            is UiState.Info -> status.text = state.message
            is UiState.Error -> {
                status.text = state.message
                summaryBox.visibility = GONE
                sectionLabel.visibility = GONE
                list.removeAllViews()
                footer.text = ""
                scroll.visibility = if (state.suggestion != null) VISIBLE else GONE
                state.suggestion?.let { s ->
                    val chip = TextView(context).apply {
                        text = "试试「" + s + "」"
                        textSize = 13f
                        setTextColor(Theme.accent)
                        setPadding(dp(12), dp(8), dp(12), dp(8))
                        background = rounded(Theme.chip, dp(10))
                        setOnClickListener {
                            input.setText(s)
                            onSubmit(s)
                        }
                    }
                    val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.topMargin = dp(10)
                    list.addView(chip, lp)
                }
            }
            is UiState.Done -> {
                val r = state.result
                status.text = (if (r.firstChapterTitle.isNotBlank()) {
                    "最早出现：" + r.firstChapterTitle
                } else if (r.firstChapterIndex >= 0) {
                    "最早出现：第 " + (r.firstChapterIndex + 1) + " 章"
                } else {
                    "最早出现：未定位"
                }) + " · 命中 " + r.hitCount + " 处"

                actionRow.visibility = if (state.summary.isNullOrBlank()) GONE else VISIBLE
                if (state.summary.isNullOrBlank()) {
                    summaryBox.visibility = GONE
                } else {
                    summaryBox.visibility = VISIBLE
                    summaryText.text = state.summary
                    val long = state.summary.length > 180
                    summaryLabel.text = "摘要 · AI 生成 · " + state.mode + (if (long) " · 可上下滑动" else "")
                    summaryScroll.layoutParams = LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        if (long) dp(180) else ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    summaryScroll.post { summaryScroll.scrollTo(0, 0) }
                }

                scroll.visibility = VISIBLE
                list.removeAllViews()
                r.aliasLong?.let { longName ->
                    val chip = TextView(context).apply {
                        text = "已排除 " + r.aliasExcluded + " 处「" + longName + "」中的匹配 · 按同一人处理"
                        textSize = 10.5f
                        setTextColor(Theme.sub)
                        setPadding(0, 0, 0, dp(8))
                        setOnClickListener { onAliasMerge() }
                    }
                    list.addView(chip)
                }
                r.sampled.take(12).forEachIndexed { index, hit ->
                    addHit(hit, r.query)
                    if (index < minOf(r.sampled.size, 12) - 1) {
                        val line = View(context).apply { setBackgroundColor(Theme.divider) }
                        val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                        lp.topMargin = dp(10)
                        lp.bottomMargin = dp(2)
                        list.addView(line, lp)
                    }
                }
                sectionLabel.visibility = if (list.childCount > 0) VISIBLE else GONE
                sectionDivider.visibility = if (list.childCount > 0) VISIBLE else GONE
                scroll.post { scroll.scrollTo(0, 0) }

                footer.text = buildString {
                    if (state.note != null) append(state.note).append(" · ")
                    append("回扫 ").append(r.scanned).append(" 章")
                    append("，取到正文 ").append(r.fetched).append(" 章")
                    if (r.truncated) append("（已达上限）")
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun addHit(hit: TraceHit, query: String) {
        val block = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(2), dp(10), dp(2), dp(6))
            setOnClickListener { onJump(hit) }
            isClickable = true
        }
        val head = TextView(context).apply {
            text = chapterLabel(hit) + " · 约第 " + pageOfHit(hit.chapterIndex, hit.position) + " 页"
            textSize = 10f
            setTextColor(Theme.faint)
        }
        val body = TextView(context).apply {
            textSize = 13f
            setTextColor(Theme.textSoft)
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, dp(4), 0, 0)
            text = highlight(hit.excerpt, query)
        }
        block.addView(head)
        block.addView(body)
        list.addView(block)
    }

    private fun chapterLabel(hit: TraceHit): String {
        val title = hit.chapterTitle.trim()
        return if (title.isNotEmpty() && CHAPTER_NO.containsMatchIn(title)) {
            title
        } else if (title.isNotEmpty()) {
            "第 " + (hit.chapterIndex + 1) + " 节 · " + title
        } else {
            "第 " + (hit.chapterIndex + 1) + " 节"
        }
    }

    private fun highlight(excerpt: String, query: String): CharSequence {
        val text = "…" + excerpt + "…"
        val sp = SpannableString(text)
        var i = text.indexOf(query)
        while (i >= 0) {
            sp.setSpan(ForegroundColorSpan(Theme.accent), i, i + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(StyleSpan(Typeface.BOLD), i, i + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = text.indexOf(query, i + query.length)
        }
        return sp
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    companion object {
        private val CHAPTER_NO = Regex("第\\s*[0-9一二三四五六七八九十百千零〇两]+\\s*[章节回卷篇话节集]")
    }
}
