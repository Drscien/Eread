package com.eread.companion

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 设置页：连接、模型、阅读、外观、数据、悬浮窗、关于。 */
class SettingsPageView(
    private val activity: MainActivity,
    private val store: Store,
) : ScrollView(activity) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val port = EditText(activity)
    private val base = EditText(activity)
    private val key = EditText(activity)
    private val model = EditText(activity)
    private val perPage = EditText(activity)
    private var refreshWindow: (() -> Unit)? = null

    init {
        isFillViewport = true
        addView(build())
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshWindow?.invoke()
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(color)
        }

    private fun build(): LinearLayout {
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = "设置"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(header)

        fun field(value: String): EditText = EditText(activity).apply {
            setText(value)
            textSize = 13f
            gravity = Gravity.END
            setTextColor(Theme.text)
            setHintTextColor(Theme.faint)
            background = null
            setPadding(0, dp(4), 0, dp(4))
            isSingleLine = true
        }

        port.setText(store.legadoPort.toString())
        base.setText(store.apiBase)
        key.setText(store.apiKey)
        key.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        model.setText(store.model)
        perPage.setText(store.charsPerPage.toString())
        listOf(port, base, key, model, perPage).forEach { fieldView(it) }

        page.addView(sectionTitle("连接"))
        val connCard = card()
        connCard.addView(inlineRow("legado 端口", port))
        page.addView(connCard)

        page.addView(sectionTitle("模型"))
        val modelCard = card()
        modelCard.addView(inlineRow("接口地址", base))
        modelCard.addView(divider())
        modelCard.addView(inlineRow("API Key", key))
        modelCard.addView(divider())
        modelCard.addView(inlineRow("模型", model))
        modelCard.addView(divider())
        modelCard.addView(levelRow())
        page.addView(modelCard)

        page.addView(sectionTitle("阅读与页码"))
        val readCard = card()
        readCard.addView(inlineRow("每页字数", perPage))
        page.addView(readCard)

        page.addView(sectionTitle("个性化"))
        val lookCard = card()
        lookCard.addView(arrowRow("主题", if (store.theme == "light") "浅色" else "深色") {
            activity.startActivity(Intent(activity, ThemeActivity::class.java))
        })
        lookCard.addView(divider())
        lookCard.addView(arrowRow("提示词", "文风与参数") {
            activity.startActivity(Intent(activity, PromptActivity::class.java))
        })
        page.addView(lookCard)

        page.addView(sectionTitle("数据"))
        val dataCard = card()
        dataCard.addView(arrowRow("Token 消耗", usageSummary()) {
            activity.startActivity(Intent(activity, UsageActivity::class.java))
        })
        page.addView(dataCard)

        page.addView(sectionTitle("悬浮窗"))
        val winCard = card()

        fun rebuildWindowRows() {
            winCard.removeAllViews()
            winCard.addView(arrowRow(
                "悬浮窗权限",
                if (Settings.canDrawOverlays(activity)) "已授权" else "未授权",
            ) {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + activity.packageName))
                )
            })
            winCard.addView(divider())
            winCard.addView(arrowRow(
                "截屏方式",
                if (store.captureMode == "projection") "屏幕录制" else "OCR识别",
            ) {
                val next = if (store.captureMode == "projection") "ocr" else "projection"
                store.captureMode = next
                if (next == "ocr") {
                    activity.stopProjection()
                    winCard.postDelayed({ rebuildWindowRows() }, 300)
                }
                rebuildWindowRows()
            })
            if (store.captureMode != "projection") {
                winCard.addView(arrowRow(
                    "OCR识别权限",
                    if (OcrService.isEnabled(activity)) "开启" else "未开启",
                    prefix = "-- ",
                ) {
                    activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                })
                winCard.addView(arrowRow(
                    "AI辅助OCR识别",
                    if (store.ocrAiFix) "开启" else "未开启",
                    prefix = "-- ",
                ) {
                    if (store.ocrAiFix) {
                        store.ocrAiFix = false
                        rebuildWindowRows()
                    } else if (store.aiNoticeShown) {
                        store.ocrAiFix = true
                        rebuildWindowRows()
                    } else {
                        AlertDialog.Builder(activity)
                            .setTitle("AI 辅助说明")
                            .setMessage("开启后，截屏识别出的文字会发送给模型做纠错，可能包含当前页正文。")
                            .setPositiveButton("开启") { _, _ ->
                                store.aiNoticeShown = true
                                store.ocrAiFix = true
                                rebuildWindowRows()
                            }
                            .setNegativeButton("取消") { _, _ -> rebuildWindowRows() }
                            .show()
                    }
                })
            } else {
                winCard.addView(arrowRow(
                    "录屏截屏授权",
                    if (ProjectionService.ready()) "已开启" else "未开启",
                    prefix = "-- ",
                ) {
                    activity.authorizeProjection()
                })
                winCard.addView(arrowRow(
                    "AI辅助录屏识别",
                    if (store.projectionAiFix) "开启" else "未开启",
                    prefix = "-- ",
                ) {
                    if (store.projectionAiFix) {
                        store.projectionAiFix = false
                        rebuildWindowRows()
                    } else if (store.aiNoticeShown) {
                        store.projectionAiFix = true
                        rebuildWindowRows()
                    } else {
                        AlertDialog.Builder(activity)
                            .setTitle("AI 辅助说明")
                            .setMessage("开启后，截屏识别出的文字会发送给模型做纠错，可能包含当前页正文。")
                            .setPositiveButton("开启") { _, _ ->
                                store.aiNoticeShown = true
                                store.projectionAiFix = true
                                rebuildWindowRows()
                            }
                            .setNegativeButton("取消") { _, _ -> rebuildWindowRows() }
                            .show()
                    }
                })
            }
        }
        rebuildWindowRows()
        refreshWindow = { rebuildWindowRows() }
        page.addView(winCard)

        page.addView(sectionTitle("关于"))
        val aboutCard = card()
        aboutCard.addView(iconRow(R.drawable.ic_info, "关于") {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        })
        aboutCard.addView(View(activity).apply { setBackgroundColor(Theme.divider) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        aboutCard.addView(iconRow(R.drawable.ic_help, "帮助") {
            HelpDialog.show(activity)
        })
        page.addView(aboutCard)

        page.addView(TextView(activity).apply {
            text = "Eread 1.0.0 · 不修改 legado，通过其 Web 服务或本地文件取书"
            textSize = 10.5f
            setTextColor(Theme.faint)
            setPadding(dp(2), dp(18), 0, 0)
        })
        return page
    }

    private fun fieldView(edit: EditText) {
        edit.textSize = 13f
        edit.gravity = Gravity.END
        edit.setTextColor(Theme.text)
        edit.setHintTextColor(Theme.faint)
        edit.background = null
        edit.setPadding(0, dp(4), 0, dp(4))
        edit.isSingleLine = true
        edit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = save()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        })
    }

    private fun save() {
        store.legadoPort = port.text.toString().trim().toIntOrNull() ?: 1122
        store.apiBase = base.text.toString().trim()
        store.apiKey = key.text.toString().trim()
        store.model = model.text.toString().trim()
        store.charsPerPage = perPage.text.toString().trim().toIntOrNull() ?: 330
    }

    private fun levelRow(): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(TextView(activity).apply {
            text = "推理等级"
            textSize = 13f
            setTextColor(Theme.text)
        })
        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, 0)
        }
        val levels = listOf("off" to "关闭", "low" to "低", "medium" to "中", "high" to "高")
        val views = ArrayList<TextView>()
        fun paint() {
            views.forEachIndexed { i, tv ->
                val active = levels[i].first == store.reasoningLevel
                tv.setTextColor(if (active) Theme.accent else Theme.sub)
                tv.background = rounded(if (active) 0x1F7FB2FF else 0xFF1A1F27.toInt(), dp(9))
            }
        }
        levels.forEach { (value, label) ->
            val tv = TextView(activity).apply {
                text = label
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener {
                    store.reasoningLevel = value
                    paint()
                }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = dp(6)
            chips.addView(tv, lp)
            views.add(tv)
        }
        paint()
        row.addView(chips)
        return row
    }

    private fun usageSummary(): String {
        val usage = UsageStore(activity)
        val totals = longArrayOf(0, 0, 0)
        usage.byFeature().values.forEach { v -> totals[0] += v[0]; totals[1] += v[1]; totals[2] += v[2] }
        val cost = (totals[0] * store.priceInput + totals[1] * store.priceOutput) / 1_000_000.0
        return String.format("¥%.3f", cost)
    }

    private fun sectionTitle(t: String): TextView = TextView(activity).apply {
        text = t
        textSize = 11f
        setTextColor(Theme.faint)
        setPadding(dp(4), dp(18), 0, dp(6))
    }

    private fun card(): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Theme.card, dp(14))
        setPadding(dp(14), dp(4), dp(14), dp(4))
    }

    private fun divider(): View = View(activity).apply {
        setBackgroundColor(Theme.divider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun inlineRow(title: String, edit: EditText): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(TextView(activity).apply {
            text = title
            textSize = 13f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(edit, LinearLayout.LayoutParams(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun arrowRow(
        title: String,
        value: String,
        prefix: String? = null,
        onClick: () -> Unit,
    ): LinearLayout = rowWithRight(title, value, true, false, onClick, prefix)

    private fun rowWithRight(
        title: String,
        value: String,
        showChevron: Boolean,
        danger: Boolean,
        onClick: () -> Unit,
        prefix: String? = null,
    ): LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(12), 0, dp(12))
        isClickable = true
        setOnClickListener { onClick() }
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (!prefix.isNullOrEmpty()) {
            titleRow.addView(TextView(activity).apply {
                text = prefix
                textSize = 13f
                setTextColor(Theme.faint)
            })
        }
        titleRow.addView(TextView(activity).apply {
            text = title
            textSize = 13f
            setTextColor(if (danger) 0xFFE57373.toInt() else Theme.text)
        })
        addView(titleRow, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(activity).apply {
            text = value
            textSize = 12f
            setTextColor(Theme.faint)
        })
        if (showChevron) {
            addView(TextView(activity).apply {
                text = "›"
                textSize = 18f
                setTextColor(Theme.faint)
                setPadding(dp(8), 0, 0, 0)
            })
        }
    }

    private fun iconRow(icon: Int, title: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { onClick() }
            addView(ImageView(activity).apply {
                setImageResource(icon)
                setColorFilter(Theme.sub)
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) })
            addView(TextView(activity).apply {
                text = title
                textSize = 13f
                setTextColor(Theme.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(activity).apply {
                text = "›"
                textSize = 18f
                setTextColor(Theme.faint)
            })
        }
}
