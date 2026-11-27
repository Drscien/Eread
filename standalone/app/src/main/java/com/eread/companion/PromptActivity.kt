package com.eread.companion

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** 自定义 AI 文风、回读、命中、关系与名称降噪参数。 */
class PromptActivity : Activity() {

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.apply(this)
        val store = Store(this)
        val styles = HashMap<String, EditText>()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.bg)
            setPadding(dp(20), dp(22), dp(20), dp(14))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "提示词与参数"
            textSize = 22f
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
            text = "点标题旁问号查看说明"
            textSize = 11.5f
            setTextColor(Theme.sub)
            setPadding(0, dp(6), 0, dp(10))
        })

        val scroll = ScrollView(this).apply { isFillViewport = true }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        var host: LinearLayout = body

        fun explain(title: String, message: String) {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show()
        }

        fun section(title: String) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = rounded(0x14FFFFFF, dp(16))
                setPadding(dp(14), dp(12), dp(14), dp(14))
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Theme.text)
            })
            body.addView(card, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) })
            host = card
        }

        fun label(text: String, desc: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                this.text = text
                textSize = 12.5f
                setTextColor(Theme.textSoft)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(TextView(this).apply {
                this.text = "?"
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(Theme.accent)
                typeface = Typeface.DEFAULT_BOLD
                background = rounded(Theme.field, dp(9)).apply { setStroke(dp(1), Theme.divider) }
                setOnClickListener { explain(text, desc) }
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { leftMargin = dp(6) })
            row.addView(android.view.View(this), LinearLayout.LayoutParams(0, dp(1), 1f))
            host.addView(row)
        }

        fun field(
            key: String,
            value: String,
            numeric: Boolean,
            decimal: Boolean = false,
            multiline: Boolean = false,
        ): EditText {
            val input = EditText(this).apply {
                setText(value)
                textSize = 13f
                setTextColor(Theme.text)
                setHintTextColor(Theme.faint)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(Theme.field, dp(10))
                if (numeric) {
                    inputType = if (decimal) {
                        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    } else {
                        InputType.TYPE_CLASS_NUMBER
                    }
                } else {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    gravity = Gravity.TOP or Gravity.START
                    minLines = if (multiline) 4 else 2
                }
            }
            styles[key] = input
            host.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return input
        }

        section("AI 文风与生成")
        label(
            "AI 文风与结构",
            "附加到所有概述、详细档案与关系生成请求，可用自然语言描述语气、人称、分段、篇幅、禁忌词。不会影响 OCR 纠错与人名抽取。",
        )
        val styleInput = field("style", store.stylePrompt, numeric = false, multiline = true)
        styleInput.hint = "例如：第三人称、学术但克制；概述分三段；详细档案按时间顺序分节。"
        label("概述温度", "0–1。越低越稳定、越贴近原文；越高越发散。默认 0.35。")
        field("summaryTemp", store.summaryTemperature.toString(), numeric = true, decimal = true)
        label("概述最大 token", "概述请求允许生成的最大长度，默认 1600。")
        field("summaryTokens", store.summaryMaxTokens.toString(), numeric = true)
        label("关系温度", "关系与补档请求的温度，默认 0.4。")
        field("relationTemp", store.relationTemperature.toString(), numeric = true, decimal = true)

        section("阅读与回读")
        label("回读分段密度", "回读缓存按全局阅读位置分桶，每 N 字一个桶。越小越精确，但回读记录更多；默认 2000。")
        field("rereadBucket", store.rereadBucket.toString(), numeric = true)

        section("检索与命中")
        label("命中展示上限", "查询结果最多展示多少条原文片段，只影响展示，不影响总命中数。默认 48。")
        field("hitSample", store.hitSample.toString(), numeric = true)
        label("每章命中上限", "同一章最多统计多少次命中，默认 500。")
        field("perChapterHits", store.perChapterHits.toString(), numeric = true)
        label("别名合并阈值", "出现多少次同样的续写才视作长名，默认 4。")
        field("aliasThreshold", store.aliasThreshold.toString(), numeric = true)
        label("定位候选章", "OCR 定位时前后各检查多少章，默认 20。")
        field("candidateChapters", store.candidateChapters.toString(), numeric = true)

        section("关系网")
        label("共现距离", "判断两人同段的最大字距，越大越宽松。默认 600。")
        field("cooccurDistance", store.cooccurDistance.toString(), numeric = true)
        label("共现片段数", "每位次人物最多保留几段共现原文，默认 2。")
        field("cooccurSnippets", store.cooccurSnippets.toString(), numeric = true)
        label("补档回看章", "人物无档案时向首次出现之后回看多少章，默认 30。")
        field("evidenceChapters", store.evidenceChapters.toString(), numeric = true)

        section("名称识别降噪")
        label("人名最短长度", "短于该长度的片段不作为人名，默认 2。")
        field("nameMin", store.nameMinLength.toString(), numeric = true)
        label("人名最长长度", "长于该长度的片段不作为人名，默认 12。")
        field("nameMax", store.nameMaxLength.toString(), numeric = true)
        label("近似名编辑距离", "OCR 近似名允许的最大编辑距离，1 较保守，2 更激进。默认 1。")
        field("nameDistance", store.nameCloseDistance.toString(), numeric = true)
        label("额外身份词", "逗号分隔。抽名时这些词会被过滤，例如：骑士团、朝圣者、异教徒。")
        field("stopWords", store.nameStopWords, numeric = false, multiline = true)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        val reset = TextView(this).apply {
            text = "恢复默认"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Theme.faint)
            setPadding(0, dp(11), 0, dp(11))
            background = rounded(Theme.field, dp(10))
            setOnClickListener {
                store.stylePrompt = ""
                store.summaryTemperature = 0.35f
                store.summaryMaxTokens = 1600
                store.relationTemperature = 0.4f
                store.rereadBucket = 2000
                store.hitSample = 48
                store.perChapterHits = 500
                store.aliasThreshold = 4
                store.candidateChapters = 20
                store.cooccurDistance = 600
                store.cooccurSnippets = 2
                store.evidenceChapters = 30
                store.nameMinLength = 2
                store.nameMaxLength = 12
                store.nameCloseDistance = 1
                store.nameStopWords = ""
                Toast.makeText(this@PromptActivity, "已恢复默认", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        actions.addView(reset, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })
        val save = TextView(this).apply {
            text = "保存"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(11), 0, dp(11))
            background = rounded(Theme.accentStrong, dp(10))
            setOnClickListener {
                fun value(key: String) = styles[key]?.text?.toString()?.trim().orEmpty()
                store.stylePrompt = value("style")
                store.summaryTemperature = value("summaryTemp").toFloatOrNull() ?: store.summaryTemperature
                store.summaryMaxTokens = value("summaryTokens").toIntOrNull() ?: store.summaryMaxTokens
                store.relationTemperature = value("relationTemp").toFloatOrNull() ?: store.relationTemperature
                store.rereadBucket = value("rereadBucket").toIntOrNull() ?: store.rereadBucket
                store.hitSample = value("hitSample").toIntOrNull() ?: store.hitSample
                store.perChapterHits = value("perChapterHits").toIntOrNull() ?: store.perChapterHits
                store.aliasThreshold = value("aliasThreshold").toIntOrNull() ?: store.aliasThreshold
                store.candidateChapters = value("candidateChapters").toIntOrNull() ?: store.candidateChapters
                store.cooccurDistance = value("cooccurDistance").toIntOrNull() ?: store.cooccurDistance
                store.cooccurSnippets = value("cooccurSnippets").toIntOrNull() ?: store.cooccurSnippets
                store.evidenceChapters = value("evidenceChapters").toIntOrNull() ?: store.evidenceChapters
                store.nameMinLength = value("nameMin").toIntOrNull() ?: store.nameMinLength
                store.nameMaxLength = value("nameMax").toIntOrNull() ?: store.nameMaxLength
                store.nameCloseDistance = value("nameDistance").toIntOrNull() ?: store.nameCloseDistance
                store.nameStopWords = value("stopWords")
                Toast.makeText(this@PromptActivity, "已保存", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        actions.addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f))
        root.addView(actions)

        setContentView(root)
    }
}
