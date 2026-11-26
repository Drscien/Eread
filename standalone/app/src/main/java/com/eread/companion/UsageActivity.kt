package com.eread.companion

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UsageActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private lateinit var store: Store
    private lateinit var balanceUrl: EditText
    private lateinit var balancePath: EditText
    private lateinit var balanceResult: TextView
    private lateinit var methodGet: TextView
    private lateinit var methodPost: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        store = Store(this)
        val usage = UsageStore(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }
        val root = ScrollView(this).apply {
            setBackgroundColor(Theme.bg)
            isFillViewport = true
        }
        root.addView(page)

        page.addView(TextView(this).apply {
            text = "Token 消耗"
            textSize = 24f
            setTextColor(Theme.text)
        })

        // ---------- 余额 ----------
        page.addView(sectionTitle("账户余额"))
        val balanceCard = card()
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(this).apply {
            text = "查询余额"
            textSize = 13f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val methodBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Theme.divider, dp(8))
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        methodGet = methodChip("GET")
        methodPost = methodChip("POST")
        methodBox.addView(methodGet)
        methodBox.addView(methodPost)
        topRow.addView(methodBox)
        balanceCard.addView(topRow)
        paintMethods()

        balanceCard.addView(fieldLabel("接口路径或完整网址"))
        balanceUrl = field(store.balanceUrl, false)
        balanceCard.addView(balanceUrl)

        balanceCard.addView(fieldLabel("结果 JSON 路径"))
        balancePath = field(store.balancePath, false)
        balanceCard.addView(balancePath)

        val queryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(2))
        }
        balanceResult = TextView(this).apply {
            text = "余额：—"
            textSize = 13f
            setTextColor(Theme.sub)
        }
        queryRow.addView(balanceResult, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        queryRow.addView(TextView(this).apply {
            text = "查询余额"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(Theme.accentStrong, dp(9))
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { queryBalance() }
        })
        balanceCard.addView(queryRow)
        page.addView(balanceCard)

        // ---------- 汇总 ----------
        page.addView(sectionTitle("累计消耗"))
        val totals = longArrayOf(0, 0, 0)
        usage.byFeature().values.forEach { v ->
            totals[0] += v[0]; totals[1] += v[1]; totals[2] += v[2]
        }
        val cost = (totals[0] * store.priceInput + totals[1] * store.priceOutput) / 1_000_000.0
        page.addView(TextView(this).apply {
            text = "输入 " + fmt(totals[0]) + " · 输出 " + fmt(totals[1]) + " · 推理 " + fmt(totals[2]) +
                "\n估算费用：¥" + String.format("%.3f", cost) +
                "（按 输入 ¥" + trim(store.priceInput) + " / 输出 ¥" + trim(store.priceOutput) + " 每百万 token）"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(dp(4), dp(2), dp(4), dp(6))
        })

        if (usage.records().isEmpty()) {
            page.addView(TextView(this).apply {
                text = "还没有调用记录"
                textSize = 13f
                setTextColor(Theme.faint)
                setPadding(dp(4), dp(10), 0, 0)
            })
        } else {
            usage.byFeature().forEach { (feature, v) ->
                val c = (v[0] * store.priceInput + v[1] * store.priceOutput) / 1_000_000.0
                page.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = rounded(Theme.card, dp(12))
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    addView(TextView(this@UsageActivity).apply {
                        text = feature
                        textSize = 14f
                        setTextColor(Theme.text)
                    })
                    addView(TextView(this@UsageActivity).apply {
                        text = "输入 " + fmt(v[0]) + " · 输出 " + fmt(v[1]) + " · 推理 " + fmt(v[2]) +
                            " · ¥" + String.format("%.3f", c)
                        textSize = 11f
                        setTextColor(Theme.sub)
                        setPadding(0, dp(4), 0, 0)
                    })
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                })
            }
            page.addView(TextView(this).apply {
                text = "清零记录"
                textSize = 13f
                setTextColor(0xFFE57373.toInt())
                setPadding(dp(4), dp(10), 0, 0)
                setOnClickListener {
                    usage.clear()
                    recreate()
                }
            })
        }

        setContentView(root)
    }

    override fun onPause() {
        saveBalanceConfig()
        super.onPause()
    }

    private fun queryBalance() {
        saveBalanceConfig()
        balanceResult.text = "余额：查询中…"
        val key = store.apiKey
        val method = store.balanceMethod
        val raw = store.balanceUrl.trim()
        val urlText = if (raw.startsWith("http://") || raw.startsWith("https://")) {
            raw
        } else {
            store.apiBase.trimEnd('/') + "/" + raw.trimStart('/')
        }
        val path = store.balancePath.trim()
        Thread {
            runCatching {
                val conn = URL(urlText).openConnection() as HttpURLConnection
                conn.requestMethod = method
                conn.connectTimeout = 12000
                conn.readTimeout = 12000
                conn.setRequestProperty("Authorization", "Bearer " + key)
                conn.setRequestProperty("Accept", "application/json")
                if (method == "POST") {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write("{}".toByteArray()) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code !in 200..299) error("接口 " + code)
                val value = pick(JSONObject(text), path)
                if (value.isBlank()) error("路径没有取到值") else value
            }.onSuccess { v ->
                runOnUiThread { balanceResult.text = "余额：¥ " + v }
            }.onFailure {
                runOnUiThread { balanceResult.text = "查询失败：" + (it.message ?: "未知") }
            }
        }.start()
    }

    private fun saveBalanceConfig() {
        if (!::balanceUrl.isInitialized) return
        store.balanceUrl = balanceUrl.text.toString().trim()
        store.balancePath = balancePath.text.toString().trim()
    }

    private fun pick(root: JSONObject, path: String): String {
        var cur: Any? = root
        for (seg in path.split('.')) {
            if (seg.isBlank()) continue
            val name = seg.substringBefore('[').ifBlank { "" }
            cur = when (val node = cur) {
                is JSONObject -> if (name.isEmpty()) node else node.opt(name)
                is JSONArray -> (name.toIntOrNull() ?: 0).let { node.opt(it) }
                else -> null
            }
            var rest = seg.substringAfter('[', "")
            while (rest.isNotEmpty()) {
                val idx = rest.substringBefore(']').toIntOrNull() ?: break
                cur = (cur as? JSONArray)?.opt(idx)
                rest = rest.substringAfter(']', "").removePrefix("[")
            }
        }
        return when (val v = cur) {
            is String -> v
            is Number -> v.toString()
            null -> ""
            else -> v.toString()
        }
    }

    private fun methodChip(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener {
            store.balanceMethod = label
            paintMethods()
        }
    }

    private fun paintMethods() {
        val get = store.balanceMethod == "GET"
        methodGet.setTextColor(if (get) Theme.accent else Theme.sub)
        methodGet.background = if (get) rounded(0x333B6FE0, dp(7)) else null
        methodPost.setTextColor(if (!get) Theme.accent else Theme.sub)
        methodPost.background = if (!get) rounded(0x333B6FE0, dp(7)) else null
    }

    private fun field(value: String, numeric: Boolean): EditText = EditText(this).apply {
        setText(value)
        textSize = 12.5f
        setTextColor(Theme.text)
        setHintTextColor(Theme.faint)
        background = rounded(Theme.divider, dp(9))
        setPadding(dp(10), dp(8), dp(10), dp(8))
        isSingleLine = true
        if (numeric) inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun fieldLabel(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 11f
        setTextColor(Theme.faint)
        setPadding(dp(2), dp(12), 0, dp(4))
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 11f
        setTextColor(Theme.faint)
        setPadding(dp(2), dp(18), 0, dp(6))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Theme.card, dp(14))
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun fmt(v: Long): String =
        if (v >= 10000) String.format("%.1f 万", v / 10000.0) else v.toString()

    private fun trim(f: Float): String =
        if (f == f.toInt().toFloat()) f.toInt().toString() else f.toString()
}
