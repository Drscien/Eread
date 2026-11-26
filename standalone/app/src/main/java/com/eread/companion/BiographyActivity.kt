package com.eread.companion

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BiographyActivity : Activity() {
    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private lateinit var bookUrl: String
    private lateinit var bookName: String
    private lateinit var character: String
    private lateinit var store: Store
    private lateinit var localStore: LocalStore
    private lateinit var state: BookState
    private lateinit var bodyText: TextView
    private lateinit var status: TextView
    private lateinit var rebuild: TextView
    private lateinit var progress: ProgressBar
    private lateinit var restore: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        bookUrl = intent.getStringExtra("bookUrl").orEmpty()
        bookName = intent.getStringExtra("bookName").orEmpty()
        character = intent.getStringExtra("character").orEmpty()
        store = Store(this)
        localStore = LocalStore(this)
        state = BookState.load(this, bookUrl, bookName)
        if (state.cards.isEmpty()) {
            BookState.all(this).firstOrNull { it.bookName == bookName }?.let { state = it }
        }
        buildUi()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        val card = state.cards[character]
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.bg)
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = character
            textSize = 22f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "返回"
            textSize = 13f
            setTextColor(Theme.accent)
            background = rounded(0x1F7FB2FF, dp(10))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { finish() }
        })
        root.addView(header)

        val level = card?.importance ?: Importance.DEFAULT
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipViews = ArrayList<TextView>()
        fun paint() {
            val cur = state.cards[character]?.importance ?: Importance.DEFAULT
            Importance.ALL.forEachIndexed { i, lv ->
                val active = lv == cur
                chipViews[i].setTextColor(if (active) Theme.accent else Theme.sub)
                chipViews[i].background = rounded(if (active) 0x1F7FB2FF else 0xFF1A1F27.toInt(), dp(10))
            }
            status.text = "本档案按「" + cur + "」生成，上限 " + Importance.bioLimit(cur) + " 字"
        }
        Importance.ALL.forEach { lv ->
            val tv = TextView(this).apply {
                text = lv
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(8), dp(6), dp(8))
                setOnClickListener {
                    val c = state.cards[character] ?: return@setOnClickListener
                    state.cards[character] = c.copy(importance = lv)
                    state.save(this@BiographyActivity)
                    paint()
                }
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = dp(5)
            chips.addView(tv, lp)
            chipViews.add(tv)
        }
        root.addView(chips)

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(Theme.sub)
            setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(status)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            this.progress = 0
            visibility = TextView.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Theme.accent)
        }
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4)).apply {
            bottomMargin = dp(6)
        })
        paint()

        val scroll = ScrollView(this)
        bodyText = TextView(this).apply {
            text = card?.biography?.takeIf { it.isNotBlank() }?.replace("**", "")
                ?: "还没有详细档案。\n\n点下方「建立新详细档案」，我会从当前页往前梳理这个人物。"
            textSize = 14.5f
            setTextColor(Theme.textSoft)
            setLineSpacing(dp(6).toFloat(), 1f)
        }
        scroll.addView(bodyText)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        rebuild = TextView(this).apply {
            text = "建立新详细档案"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = rounded(Theme.accentStrong, dp(12))
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { confirmBuild() }
        }
        actions.addView(rebuild, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        restore = TextView(this).apply {
            text = "恢复上一版"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(dp(14), dp(12), dp(2), dp(12))
            visibility = if (card?.bioPrev?.isNotBlank() == true) TextView.VISIBLE else TextView.GONE
            setOnClickListener { restorePrev() }
        }
        actions.addView(restore)
        root.addView(actions)
        setContentView(root)
    }

    private fun confirmBuild() {
        val card = state.cards[character] ?: return
        val anchor = Position.chapterOf(state.latestPos)
        val level = card.importance
        val readChars = maxOf(1, anchor) * 3000
        val scale = (readChars / 800000f).coerceIn(0.2f, 1f)
        val est = (Importance.bioLimit(level) * scale).toInt().coerceAtLeast(600)
        AlertDialog.Builder(this)
            .setTitle("建立新详细档案？")
            .setMessage("将基于当前页之前的内容重写，预计约 " + est + " 字，可能需要 1-3 分钟。")
            .setPositiveButton("开始") { _, _ -> startBuild() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun startBuild() {
        if (busy) return
        busy = true
        rebuild.text = "生成中…"
        rebuild.isEnabled = false
        val card = state.cards[character] ?: return
        val anchor = Position.chapterOf(state.latestPos)
        status.text = "准备中…"
        scope.launch {
            runCatching {
                val book = BookInfo(bookUrl.ifBlank { state.bookUrl }, bookName, "", anchor, 0, 0)
                BiographyBuilder(store, UsageStore(this@BiographyActivity), localStore, LegadoApi(store.legadoPort))
                    .build(book, character, anchor, card.importance) { done, total, msg ->
                        runOnUiThread {
                            status.text = msg
                            progress.visibility = TextView.VISIBLE
                            progress.progress = if (total <= 0) 0 else (done * 100 / total)
                        }
                    }
            }.onSuccess { text ->
                val old = state.cards[character]
                state.cards[character] = (old ?: return@onSuccess).copy(
                    biography = text,
                    bioPrev = old.biography,
                    bioPos = state.latestPos,
                    bioAt = System.currentTimeMillis(),
                )
                state.save(this@BiographyActivity)
                bodyText.text = text.replace("**", "")
                status.text = "已生成 · " + text.length + " 字"
                progress.visibility = TextView.GONE
                restore.visibility = TextView.VISIBLE
            }.onFailure {
                status.text = "失败：" + (it.message ?: "未知")
                progress.visibility = TextView.GONE
            }
            busy = false
            rebuild.text = "建立新详细档案"
            rebuild.isEnabled = true
        }
    }

    private fun restorePrev() {
        val card = state.cards[character] ?: return
        if (card.bioPrev.isBlank()) return
        state.cards[character] = card.copy(biography = card.bioPrev, bioPrev = "")
        state.save(this)
        bodyText.text = card.bioPrev
        restore.visibility = TextView.GONE
        Toast.makeText(this, "已恢复上一版", Toast.LENGTH_SHORT).show()
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }
}
