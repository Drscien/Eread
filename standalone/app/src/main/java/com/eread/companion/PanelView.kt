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
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
    private val onOcrCalibrate: () -> Unit,
    private val onPickChapter: () -> Unit,
    private val onRebuildIndex: () -> Unit,
    private val onReadestOcr: () -> Unit,
    private val onRelPickMain: () -> Unit,
    private val onRelPickSecondary: () -> Unit,
    private val onRelGenerate: (String, List<String>) -> Unit,
    private val onRelTimeline: () -> Unit,
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
    private val calibHint = TextView(context)
    private val calibBtn = TextView(context)
    private val chapterLabel = TextView(context)
    private val chapterRow = LinearLayout(context)
    private val calibOcrBtn = TextView(context)
    private val calibIndexBtn = TextView(context)
    private var readest = false

    private val sourceBadge = TextView(context)
    private val identifyButton = TextView(context)
    private val identifyStatus = TextView(context)
    private val identifyList = LinearLayout(context)
    private val relationPane = LinearLayout(context)
    private val relMainLabel = TextView(context)
    private val relPickMainBtn = TextView(context)
    private val relPickSecBtn = TextView(context)
    private val relClearBtn = TextView(context)
    private val relSecList = LinearLayout(context)
    private val relSecTitle = TextView(context)
    private val relResult = TextView(context)
    private val relTimelineBtn = TextView(context)
    private val searchOcrBtn = TextView(context)
    private val recentRow = LinearLayout(context)
    private val recentScroll = HorizontalScrollView(context)
    private var relMain: String = ""
    private var relBusy = false
    private val relSelected = ArrayList<String>()

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
        sourceBadge.textSize = 8.5f
        sourceBadge.setPadding(dp(6), dp(2), dp(6), dp(2))
        sourceBadge.background = rounded(0xFF1B2740.toInt(), dp(6))
        sourceBadge.setTextColor(0xFF7FB2FF.toInt())
        val badgeLp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        badgeLp.rightMargin = dp(6)
        header.addView(sourceBadge, badgeLp)
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

        searchOcrBtn.text = "截屏识别当前页"
        searchOcrBtn.textSize = 14f
        searchOcrBtn.gravity = Gravity.CENTER
        searchOcrBtn.setTextColor(Color.WHITE)
        searchOcrBtn.setPadding(0, dp(11), 0, dp(11))
        searchOcrBtn.background = rounded(0xFF2F7B55.toInt(), dp(10))
        searchOcrBtn.visibility = GONE
        searchOcrBtn.setOnClickListener { if (searchOcrBtn.isEnabled) onReadestOcr() }
        val ocrLp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ocrLp.topMargin = dp(10)
        searchPane.addView(searchOcrBtn, ocrLp)

        recentRow.orientation = HORIZONTAL
        recentRow.setPadding(0, dp(8), 0, 0)
        recentScroll.isHorizontalScrollBarEnabled = false
        recentScroll.visibility = GONE
        recentScroll.addView(recentRow)
        searchPane.addView(recentScroll)

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
            if (!identifyButton.isEnabled) return@setOnClickListener
            markIdentifying()
            if (readest) onReadestOcr() else onIdentify()
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
        calibHint.textSize = 12f
        calibHint.setTextColor(Theme.sub)
        calibHint.setPadding(0, dp(10), 0, dp(10))
        calibPane.addView(calibHint)
        chapterRow.orientation = HORIZONTAL
        chapterRow.gravity = Gravity.CENTER_VERTICAL
        chapterRow.visibility = GONE
        chapterLabel.text = "当前章节：—"
        chapterLabel.textSize = 12f
        chapterLabel.setTextColor(Theme.textSoft)
        chapterRow.addView(chapterLabel, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        chapterRow.addView(TextView(context).apply {
            text = "选择章节"
            textSize = 12f
            setTextColor(Theme.accent)
            setPadding(dp(10), dp(8), dp(2), dp(8))
            setOnClickListener { onPickChapter() }
        })
        calibPane.addView(chapterRow)

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
        calibBtn.text = "校准"
        calibBtn.gravity = Gravity.CENTER
        calibBtn.setPadding(dp(14), 0, dp(14), 0)
        calibBtn.textSize = 14f
        calibRow.addView(calibBtn.apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Theme.accentStrong, dp(8))
            setOnClickListener {
                val value = calibInput.text?.toString()?.trim()?.toIntOrNull()
                if (value == null || value <= 1) {
                    calibStatus.text = "页码要大于 1"
                } else {
                    hideKeyboard()
                    calibStatus.text = "校准中…"
                    onCalibrate(value)
                }
            }
        }, LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { leftMargin = dp(8) })
        calibPane.addView(calibRow)

        calibOcrBtn.textSize = 12.5f
        calibOcrBtn.gravity = Gravity.CENTER
        calibOcrBtn.setTextColor(Theme.accent)
        calibOcrBtn.setPadding(0, dp(10), 0, dp(10))
        calibOcrBtn.background = rounded(Theme.cardSel, dp(9))
        calibOcrBtn.setOnClickListener { onOcrCalibrate() }
        calibPane.addView(calibOcrBtn, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        calibIndexBtn.text = "重建人名索引（全书扫描）"
        calibIndexBtn.textSize = 12f
        calibIndexBtn.gravity = Gravity.CENTER
        calibIndexBtn.setTextColor(Theme.faint)
        calibIndexBtn.setPadding(0, dp(9), 0, dp(9))
        calibIndexBtn.background = rounded(Theme.field, dp(9))
        calibIndexBtn.setOnClickListener { onRebuildIndex() }
        calibPane.addView(calibIndexBtn, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(8)
        })

        calibStatus.textSize = 12f
        calibStatus.setTextColor(Theme.accent)
        calibStatus.setPadding(0, dp(10), 0, 0)
        calibPane.addView(calibStatus)

        relationPane.orientation = VERTICAL
        relationPane.visibility = GONE
        val relMainRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        relMainLabel.text = "主人物：未选择"
        relMainLabel.textSize = 12.5f
        relMainLabel.setTextColor(Theme.textSoft)
        relMainRow.addView(relMainLabel, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        relPickMainBtn.text = "选择主人物"
        relPickMainBtn.textSize = 12f
        relPickMainBtn.setTextColor(Theme.accent)
        relPickMainBtn.setPadding(dp(8), dp(8), dp(2), dp(8))
        relPickMainBtn.setOnClickListener { onRelPickMain() }
        relMainRow.addView(relPickMainBtn)
        relationPane.addView(relMainRow)

        val relSecHeader = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        relSecTitle.text = "次人物（0）"
        relSecTitle.textSize = 12.5f
        relSecTitle.setTextColor(Theme.textSoft)
        relSecHeader.addView(relSecTitle, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        relClearBtn.text = "清除"
        relClearBtn.textSize = 12f
        relClearBtn.setTextColor(0xFFFF7B7B.toInt())
        relClearBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        relClearBtn.setOnClickListener { clearRelationSecondary() }
        relSecHeader.addView(relClearBtn)

        relPickSecBtn.text = "选择次人物"
        relPickSecBtn.textSize = 12f
        relPickSecBtn.setTextColor(Theme.accent)
        relPickSecBtn.setPadding(dp(8), dp(8), dp(8), dp(8))
        relPickSecBtn.setOnClickListener { onRelPickSecondary() }
        relSecHeader.addView(relPickSecBtn)
        relationPane.addView(relSecHeader)

        val relScroll = MaxHeightScrollView(context, dp(170)).apply { isVerticalScrollBarEnabled = false }
        relSecList.orientation = VERTICAL
        relScroll.addView(relSecList)
        val relScrollLp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        relScrollLp.topMargin = dp(4)
        relationPane.addView(relScroll, relScrollLp)

        relationPane.addView(android.view.View(context).apply { setBackgroundColor(Theme.divider) },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(10) })

        val relGen = TextView(context).apply {
            text = "生成关系列表"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(11), 0, dp(11))
            background = rounded(Theme.accentStrong, dp(10))
        }
        relGen.setOnClickListener {
            if (relBusy) return@setOnClickListener
            if (relMain.isBlank()) {
                relResult.text = "先选择主人物"
            } else if (relSelected.isEmpty()) {
                relResult.text = "至少选择一位次人物"
            } else {
                relResult.text = "生成中…"
                onRelGenerate(relMain, relSelected.toList())
            }
        }
        relationPane.addView(relGen, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        relationPane.addView(TextView(context).apply {
            text = "关系列表"
            textSize = 12.5f
            setTextColor(Theme.textSoft)
            setPadding(0, dp(12), 0, 0)
        })
        relResult.textSize = 12.5f
        relResult.setTextColor(Theme.textSoft)
        relResult.setLineSpacing(dp(4).toFloat(), 1f)
        relResult.setPadding(dp(10), dp(9), dp(10), dp(9))
        relResult.background = rounded(Theme.field, dp(10)).apply { setStroke(dp(1), Theme.divider) }
        val relResultScroll = MaxHeightScrollView(context, dp(220)).apply { isVerticalScrollBarEnabled = false }
        relResultScroll.addView(relResult)
        relationPane.addView(relResultScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        relTimelineBtn.text = "关系时间线"
        relTimelineBtn.setOnClickListener { onRelTimeline() }
        relTimelineBtn.textSize = 12f
        relTimelineBtn.gravity = Gravity.CENTER
        relTimelineBtn.setTextColor(Theme.faint)
        relTimelineBtn.setPadding(0, dp(9), 0, dp(9))
        relTimelineBtn.background = rounded(Theme.field, dp(9))
        relationPane.addView(relTimelineBtn, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        addView(header)
        addView(searchPane)
        addView(identifyPane)
        addView(relationPane)
        addView(calibPane)

        switchTab(0)
    }


    private fun switchTab(index: Int) {
        mode = index
        searchPane.visibility = if (index == 0) VISIBLE else GONE
        identifyPane.visibility = if (index == 1 && !readest) VISIBLE else GONE
        relationPane.visibility = if (index == 1 && readest) VISIBLE else GONE
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

    fun showNames(names: List<String>, statusText: String = "点名字直接去查：") {
        identifyButton.isEnabled = true
        identifyButton.text = "快速识别人名"
        identifyButton.background = rounded(Theme.accentStrong, dp(12))
        identifyList.removeAllViews()
        if (names.isEmpty()) {
            identifyStatus.text = "这一页没识别到人名，换个位置再试"
            return
        }
        identifyStatus.text = statusText
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

    fun setSource(source: String) {
        readest = source == "readest"
        sourceBadge.text = if (readest) "READEST" else "LEGADO"
        sourceBadge.setTextColor(if (readest) 0xFF3FA46A.toInt() else 0xFF7FB2FF.toInt())
        tabIdentify.text = if (readest) "截屏识别" else "快速识别"
        identifyButton.text = if (readest) "截屏识别当前页" else "快速识别人名"
        calibHint.text = if (readest) {
            "先选择章节，或直接点截屏校准自动定位。\n填阅读器底部页码后点「校准」可手动修正。"
        } else {
            "填入 legado 底部显示的页码。\n校准后，同一章的片段页码会用它做插值。"
        }
        calibOcrBtn.text = if (readest) "截屏校准（读页码 + 定位）" else "截屏读取页码"
        calibInput.hint = if (readest) "例如 12（页码）" else "例如 12"
        calibBtn.text = "校准"
        chapterRow.visibility = if (readest) VISIBLE else GONE
        calibIndexBtn.visibility = if (readest) VISIBLE else GONE
        sectionLabel.text = if (readest) "原文片段 · 点击复制" else "原文片段 · 点击可跳转"
        tabIdentify.text = if (readest) "关系网" else "快速识别"
        searchOcrBtn.visibility = if (readest) VISIBLE else GONE
        identifyPane.visibility = if (mode == 1 && !readest) VISIBLE else GONE
        relationPane.visibility = if (mode == 1 && readest) VISIBLE else GONE
    }

    fun setBookLabel(text: String) {
        bookLabel.text = text
        calibBookLabel.text = text
    }

    fun setSearchStatus(text: String) {
        status.text = text
    }

    fun setOcrBusy(busy: Boolean) {
        searchOcrBtn.isEnabled = !busy
        searchOcrBtn.text = if (busy) "识别中…" else "截屏识别当前页"
        searchOcrBtn.background = rounded(if (busy) 0xFF2A3140.toInt() else 0xFF2F7B55.toInt(), dp(10))
    }

    fun setRecentNames(names: List<String>) {
        recentRow.removeAllViews()
        recentScroll.visibility = if (names.isEmpty()) GONE else VISIBLE
        if (names.isEmpty()) return
        names.take(12).forEach { name ->
            val chip = TextView(context).apply {
                text = name
                textSize = 12.5f
                setTextColor(0xFF9CC0FF.toInt())
                gravity = Gravity.CENTER
                background = rounded(Theme.chip, dp(9))
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener {
                    input.setText(name)
                    onSubmit(name)
                }
            }
            val lp = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            recentRow.addView(chip, lp)
        }
    }

    fun setRelationMain(name: String) {
        if (name != relMain) {
            relSelected.clear()
            renderRelationList()
        }
        relMain = name
        relMainLabel.text = if (name.isBlank()) "主人物：未选择" else "主人物：" + name
        relResult.text = "选择次人物后生成关系"
    }

    fun relationMain(): String = relMain

    fun addRelationSecondary(items: List<String>) {
        items.forEach { if (it !in relSelected) relSelected.add(it) }
        renderRelationList()
    }

    fun clearRelationSecondary() {
        relSelected.clear()
        renderRelationList()
        relResult.text = "至少选择一位次人物"
    }

    fun setRelationBusy(busy: Boolean) {
        relBusy = busy
    }

    fun relationSelected(): List<String> = relSelected.toList()

    fun relationText(): String = relResult.text?.toString().orEmpty()

    private fun renderRelationList() {
        relSecList.removeAllViews()
        relSecTitle.text = "次人物（" + relSelected.size + "）"
        relSelected.forEach { name ->
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = rounded(Theme.panel, dp(8)).apply { setStroke(dp(1), Theme.divider) }
                setPadding(dp(10), 0, dp(4), 0)
            }
            row.addView(TextView(context).apply {
                text = name
                textSize = 12.5f
                setTextColor(Theme.text)
                setPadding(0, dp(8), 0, dp(8))
            }, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(TextView(context).apply {
                text = "×"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(0xFFFF7B7B.toInt())
                setPadding(dp(10), dp(4), dp(10), dp(4))
                setOnClickListener {
                    relSelected.remove(name)
                    renderRelationList()
                }
            })
            relSecList.addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(5)
            })
        }
    }

    fun setRelationCandidates(items: List<String>) {
        addRelationSecondary(items)
    }

    fun setRelationResult(text: String) {
        relResult.visibility = if (text.isBlank()) GONE else VISIBLE
        if (relSelected.isEmpty()) {
            relResult.text = text
            return
        }
        val span = android.text.SpannableString(text)
        relSelected.forEach { name ->
            var index = text.indexOf(name)
            while (index >= 0) {
                span.setSpan(
                    android.text.style.ForegroundColorSpan(Theme.accent),
                    index, index + name.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                span.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    index, index + name.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                index = text.indexOf(name, index + name.length)
            }
        }
        relResult.text = span
    }

    fun setChapterLabel(text: String) {
        chapterLabel.text = text
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
            is UiState.Choose -> {
                status.text = "「" + state.query + "」可能指："
                summaryBox.visibility = GONE
                actionRow.visibility = GONE
                sectionLabel.visibility = GONE
                sectionDivider.visibility = GONE
                footer.text = ""
                scroll.visibility = VISIBLE
                list.removeAllViews()
                state.candidates.forEach { entity ->
                    val subtitle = if (entity.latin.isNotBlank()) " · " + entity.latin else ""
                    val rowView = TextView(context).apply {
                        text = entity.displayName + subtitle + "\n命中 " + entity.count + " 次"
                        textSize = 13f
                        setTextColor(Theme.textSoft)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        setLineSpacing(dp(3).toFloat(), 1f)
                        background = rounded(Theme.card, dp(10))
                        isClickable = true
                        setOnClickListener {
                            input.setText(entity.full)
                            onSubmit(entity.full)
                        }
                    }
                    val lp = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    lp.bottomMargin = dp(8)
                    list.addView(rowView, lp)
                }
                val literal = TextView(context).apply {
                    text = "都不是，按字面搜索「" + state.query + "」"
                    textSize = 12f
                    setTextColor(Theme.accent)
                    setPadding(dp(4), dp(6), 0, dp(4))
                    setOnClickListener { onSubmit(state.query) }
                }
                list.addView(literal)
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
