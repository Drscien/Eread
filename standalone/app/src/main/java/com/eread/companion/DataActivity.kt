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
        runCatching {
            var dirty = false
            state.cards.entries.toList().forEach { (name, card) ->
                if (card.relations.contains("资料未载直接关系者") || card.relations.contains("B 是 A 的")) {
                    val cleaned = card.relations.lines()
                        .filterNot { it.contains("资料未载直接关系者") || it.contains("B 是 A 的") }
                        .joinToString("\n").trim()
                    state.cards[name] = card.copy(relations = cleaned)
                    dirty = true
                }
            }
            if (dirty) state.save(this)
        }
        // 用已有的书名索引清理历史误建的歧义档案，并把英文名换成中文全名
        runCatching {
            val local = LocalStore(this).findMatching(bookName) ?: return@runCatching
            val index = NameIndex(NameIndexStore.load(this, local))
            if (index.entities.isEmpty()) return@runCatching
            var changed = false
            state.cards.keys.toList().forEach { rawKey ->
                val key = NameParser.cleanDescriptor(rawKey)
                if (key != rawKey) {
                    val moved = state.cards.remove(rawKey) ?: return@forEach
                    state.cards[key] = moved.copy(name = key)
                    changed = true
                }
                val self = index.entities.firstOrNull { it.full == key }
                val near = NameMatcher.nearest(
                    key,
                    index.entities.filter { it.full != key && (self == null || it.count > self.count) },
                    Store(this@DataActivity).nameCloseDistance,
                )
                if (near != null) {
                    val from = state.cards.remove(key) ?: return@forEach
                    val to = state.cards[near.full]
                    state.cards[near.full] = if (to == null) {
                        from.copy(name = near.full, latin = near.latin.ifBlank { from.latin })
                    } else {
                        to.copy(
                            summary = listOf(to.summary, from.summary).filter { it.isNotBlank() }.joinToString("\n").take(600),
                            relations = mergeRelationText(to.relations, from.relations),
                        )
                    }
                    changed = true
                    return@forEach
                }
                when (val r = index.resolve(key)) {
                    is NameResolution.NotFound -> {
                        val near = NameMatcher.nearest(key, index.entities)
                        if (near != null && near.full != key) {
                            val from = state.cards.remove(key) ?: return@forEach
                            val to = state.cards[near.full]
                            state.cards[near.full] = if (to == null) {
                                from.copy(name = near.full, latin = near.latin.ifBlank { from.latin })
                            } else {
                                to.copy(
                                    summary = listOf(to.summary, from.summary).filter { it.isNotBlank() }.joinToString("\n").take(600),
                                    relations = mergeRelationText(to.relations, from.relations),
                                )
                            }
                            changed = true
                        }
                    }
                    is NameResolution.Ambiguous -> {
                        val card = state.cards[key]
                        if (card != null && card.summary.isBlank() && card.biography.isBlank() && card.relations.isBlank()) {
                            state.cards.remove(key)
                            changed = true
                        }
                    }
                    is NameResolution.Unique -> {
                        val full = r.entity.full
                        if (full != key) {
                            val card = state.cards.remove(key) ?: return@forEach
                            state.cards[full] = card.copy(
                                name = full,
                                latin = r.entity.latin.ifBlank { card.latin },
                            )
                            changed = true
                        }
                    }
                    else -> Unit
                }
            }
            if (changed) state.save(this)
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
                body.addView(
                    cardView(card, state),
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(10)
                    },
                )
            }
        }

        scroll.addView(body)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun mergeRelationText(a: String, b: String): String {
        val lines = LinkedHashSet<String>()
        (a + "\n" + b).lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { lines.add(it) }
        return lines.joinToString("\n")
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
        val nameBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        nameBox.addView(TextView(this).apply {
            text = card.name
            textSize = 15f
            setTextColor(Theme.text)
        })
        if (card.latin.isNotBlank()) {
            nameBox.addView(TextView(this).apply {
                text = card.latin
                textSize = 10.5f
                setTextColor(Theme.faint)
                setPadding(0, dp(2), 0, 0)
            })
        }
        top.addView(nameBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(TextView(this).apply {
            text = card.importance
            textSize = 10f
            setTextColor(Theme.accent)
            background = rounded(0x1F7FB2FF, dp(8))
            setPadding(dp(8), dp(3), dp(8), dp(3))
        })
        box.addView(top)
        box.addView(TextView(this).apply {
            val place = if (card.pos > 0) formatPos(card.pos) else "位置未记录"
            text = place + " · 更新 " + card.updates + " 次 · " + time(card.updatedAt)
            textSize = 10.5f
            setTextColor(Theme.faint)
            setPadding(0, dp(5), 0, dp(6))
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun actionButton(label: String, accent: Boolean, onClick: () -> Unit): TextView =
            TextView(this).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
                isSingleLine = true
                setTextColor(if (accent) Theme.accent else 0xFFE57373.toInt())
                setPadding(dp(2), dp(7), dp(2), dp(7))
                background = rounded(if (accent) Theme.chip else 0xFF2A1C1F.toInt(), dp(8))
                setOnClickListener { onClick() }
            }

        fun actionParams(last: Boolean) = LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f,
        ).apply { if (!last) rightMargin = dp(6) }
        actions.addView(actionButton("最新概述", true) {
            startActivity(android.content.Intent(this@DataActivity, SummaryActivity::class.java)
                .putExtra("bookUrl", state.bookUrl)
                .putExtra("bookName", state.bookName)
                .putExtra("character", card.name))
        }, actionParams(false))
        actions.addView(actionButton("详细档案", true) {
            startActivity(android.content.Intent(this@DataActivity, BiographyActivity::class.java)
                .putExtra("bookUrl", state.bookUrl)
                .putExtra("bookName", state.bookName)
                .putExtra("character", card.name))
        }, actionParams(false))
        actions.addView(actionButton("人物关系", true) {
            startActivity(android.content.Intent(this@DataActivity, RelationActivity::class.java)
                .putExtra("bookUrl", state.bookUrl)
                .putExtra("bookName", state.bookName)
                .putExtra("character", card.name))
        }, actionParams(false))
        actions.addView(actionButton("删除", false) {
            android.app.AlertDialog.Builder(this@DataActivity)
                .setTitle("删除人物档案")
                .setMessage("确定删除「" + card.name + "」的档案？")
                .setPositiveButton("删除") { _, _ ->
                    state.cards.remove(card.name)
                    state.save(this@DataActivity)
                    recreate()
                }
                .setNegativeButton("取消", null)
                .show()
        }, actionParams(true))
        box.addView(actions)
        return LinearLayout(this).apply {
            addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
