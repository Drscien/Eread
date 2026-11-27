package com.eread.companion

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.AlertDialog

/** 数据页：书目列表、多选、导出/分享/删除。 */
class LibraryPageView(
    private val activity: MainActivity,
    private val store: Store,
    private val localStore: LocalStore,
    private val onSelectionMode: (Boolean) -> Unit,
) : FrameLayout(activity) {

    private val d = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private val list = LinearLayout(activity)
    private val help = TextView(activity)
    private val close = TextView(activity)
    private val deleteBar = LinearLayout(activity)

    private var selecting = false
    private val selected = HashSet<String>()

    data class Row(
        val key: String,
        val name: String,
        val subtitle: String,
        val isLocal: Boolean,
        val bookUrl: String,
        val fileName: String,
    )

    init {
        addView(buildPage(), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        buildDeleteBar()
        addView(deleteBar, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
        ).apply { bottomMargin = dp(24) })
        deleteBar.visibility = View.GONE
        refresh()
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    private fun buildPage(): View {
        val page = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(activity).apply {
            text = "数据"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        help.text = "?"
        help.textSize = 13f
        help.gravity = Gravity.CENTER
        help.setTextColor(Theme.sub)
        help.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(1), Theme.divider)
        }
        help.setOnClickListener { showHelp() }
        header.addView(help, LinearLayout.LayoutParams(dp(22), dp(22)))
        close.text = "×"
        close.textSize = 15f
        close.gravity = Gravity.CENTER
        close.setTextColor(Color.WHITE)
        close.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0xFFE87A9B.toInt())
        }
        close.visibility = View.GONE
        close.setOnClickListener { exitSelection() }
        header.addView(close, LinearLayout.LayoutParams(dp(22), dp(22)))
        page.addView(header)
        page.addView(TextView(activity).apply {
            text = "导入过的本地书、查过人物的书，都会在这里留下档案。"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(6), 0, dp(12))
        })
        list.orientation = LinearLayout.VERTICAL
        page.addView(list)
        page.addView(TextView(activity).apply {
            text = "在「运行」页导入新文件"
            textSize = 12f
            setTextColor(Theme.faint)
            setPadding(0, dp(16), 0, 0)
        })
        page.isClickable = true
        page.setOnClickListener { if (selecting) exitSelection() }
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(page)
            isClickable = true
            setOnClickListener { if (selecting) exitSelection() }
        }
        return scroll
    }

    private fun baseName(raw: String): String =
        raw.substringBefore(" 作者").substringBefore("（").trim()

    fun rows(): List<Row> {
        val locals = localStore.books()
        val states = runCatching { BookState.all(activity) }.getOrDefault(emptyList())
        val localNames = locals.map { baseName(it.name) }.toSet()
        val out = ArrayList<Row>()
        locals.forEach { b ->
            val st = states.firstOrNull { baseName(it.bookName) == baseName(b.name) }
            val cards = st?.cards?.size ?: 0
            val extra = if (cards > 0) " · " + cards + " 个人物" else ""
            out.add(Row(
                "L:" + b.fileName,
                b.name,
                b.chapters.size.toString() + " 节 · 已导入" + extra,
                true,
                st?.bookUrl ?: "",
                b.fileName,
            ))
        }
        states.filter { baseName(it.bookName) !in localNames }
            .filter { it.cards.isNotEmpty() || it.latestPos > 0 }
            .sortedByDescending { st -> st.cards.values.maxOfOrNull { c -> c.updatedAt } ?: 0L }
            .forEach { st ->
                out.add(Row(
                    "S:" + st.bookUrl,
                    st.bookName,
                    "接口书 · " + st.cards.size + " 个人物",
                    false,
                    st.bookUrl,
                    "",
                ))
            }
        return out
    }

    private var gen = 0

    fun refresh() {
        val current = ++gen
        Thread {
            val rows = runCatching { rows() }.getOrDefault(emptyList())
            activity.runOnUiThread { if (current == gen) render(rows) }
        }.start()
    }

    private fun render(rows: List<Row>) {
        list.removeAllViews()
        if (rows.isEmpty()) {
            list.addView(TextView(activity).apply {
                text = "还没有记录。导入本地文件，或在阅读时查一次人物。"
                textSize = 13f
                setTextColor(Theme.faint)
                setPadding(0, dp(20), 0, dp(20))
            })
            return
        }
        rows.forEach { rowData ->
            val isSel = selected.contains(rowData.key)
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(if (isSel) Theme.cardSel else Theme.card, dp(10))
                isClickable = true
                setOnClickListener {
                    if (selecting) setSelected(rowData.key, !selected.contains(rowData.key))
                    else openBook(rowData)
                }
                setOnLongClickListener {
                    if (!selecting) enterSelection(rowData.key)
                    true
                }
            }
            val info = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(activity).apply {
                text = rowData.name
                textSize = 14f
                setTextColor(Theme.text)
            })
            info.addView(TextView(activity).apply {
                text = rowData.subtitle
                textSize = 11f
                setTextColor(Theme.sub)
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (selecting) {
                val cb = CheckBox(activity).apply {
                    isChecked = isSel
                    buttonTintList = ColorStateList.valueOf(Theme.accent)
                    setOnCheckedChangeListener { _, checked -> setSelected(rowData.key, checked) }
                }
                row.addView(cb, LinearLayout.LayoutParams(dp(40), dp(40)))
            } else {
                row.addView(TextView(activity).apply {
                    text = "档案"
                    textSize = 12f
                    setTextColor(Theme.accent)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener { openBook(rowData) }
                })
            }
            list.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }
    }

    private fun openBook(row: Row) {
        activity.startActivity(
            Intent(activity, DataActivity::class.java)
                .putExtra("bookUrl", row.bookUrl)
                .putExtra("bookName", row.name)
        )
    }

    private fun setSelected(key: String, on: Boolean) {
        if (on) selected.add(key) else selected.remove(key)
        refresh()
    }

    private fun enterSelection(key: String) {
        selecting = true
        help.visibility = View.GONE
        close.visibility = View.VISIBLE
        selected.clear()
        selected.add(key)
        onSelectionMode(true)
        showDeleteBar()
        refresh()
    }

    fun exitSelection() {
        if (!selecting) return
        selecting = false
        help.visibility = View.VISIBLE
        close.visibility = View.GONE
        selected.clear()
        onSelectionMode(false)
        hideDeleteBar()
        refresh()
    }

    fun isSelecting(): Boolean = selecting

    private fun buildDeleteBar() {
        deleteBar.orientation = LinearLayout.HORIZONTAL
        deleteBar.gravity = Gravity.CENTER
        deleteBar.addView(circle(R.drawable.ic_export, Theme.accentStrong) { exportSelected() })
        deleteBar.addView(circle(R.drawable.ic_share, 0xFF3FA46A.toInt()) { shareSelected() })
        deleteBar.addView(circle(R.drawable.ic_delete, 0xFFC0453F.toInt()) { deleteSelected() })
    }

    private fun circle(iconRes: Int, color: Int, onClick: () -> Unit): View {
        val wrap = LinearLayout(activity).apply { gravity = Gravity.CENTER }
        val shape = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            elevation = dp(8).toFloat()
            setOnClickListener { onClick() }
        }
        shape.addView(ImageView(activity).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        wrap.addView(shape, LinearLayout.LayoutParams(dp(58), dp(58)))
        return wrap.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun showDeleteBar() {
        deleteBar.visibility = View.VISIBLE
        deleteBar.translationY = dp(90).toFloat()
        deleteBar.alpha = 0f
        deleteBar.animate().translationY(0f).alpha(1f).setDuration(220).start()
    }

    private fun hideDeleteBar() {
        deleteBar.animate().translationY(dp(90).toFloat()).alpha(0f).setDuration(180)
            .withEndAction { deleteBar.visibility = View.GONE }
            .start()
    }

    private fun targets(): List<Row> = rows().filter { selected.contains(it.key) }

    private fun exportSelected() {
        val rows = targets()
        if (rows.isEmpty()) {
            Toast.makeText(activity, "先勾选要导出的条目", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("导出 " + rows.size + " 本书的档案")
            .setItems(arrayOf("TXT 文本", "Word 文档（.docx）")) { _, which ->
                val format = if (which == 0) BookExporter.Format.TXT else BookExporter.Format.DOCX
                doExport(rows, format)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doExport(rows: List<Row>, format: BookExporter.Format) {
        Toast.makeText(activity, "正在导出…", Toast.LENGTH_SHORT).show()
        Thread {
            val states = BookState.all(activity).filter { st -> rows.any { it.name == st.bookName } }
            val paths = runCatching { BookExporter.export(activity, states, format) }.getOrDefault(emptyList())
            activity.runOnUiThread {
                Toast.makeText(
                    activity,
                    if (paths.isEmpty()) "导出失败：这些书还没有人物档案" else "已导出 " + paths.size + " 个压缩包到 Download/Eread",
                    Toast.LENGTH_LONG,
                ).show()
                if (paths.isNotEmpty()) exitSelection()
            }
        }.start()
    }

    private fun shareSelected() {
        val rows = targets()
        if (rows.isEmpty()) {
            Toast.makeText(activity, "先勾选要分享的条目", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle("分享 " + rows.size + " 本书的档案")
            .setItems(arrayOf("TXT 文本", "Word 文档（.docx）")) { _, which ->
                val format = if (which == 0) BookExporter.Format.TXT else BookExporter.Format.DOCX
                doShare(rows, format)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doShare(rows: List<Row>, format: BookExporter.Format) {
        Toast.makeText(activity, "正在打包…", Toast.LENGTH_SHORT).show()
        Thread {
            val states = BookState.all(activity).filter { st -> rows.any { it.name == st.bookName } }
            val files = runCatching { BookExporter.exportToCache(activity, states, format) }.getOrDefault(emptyList())
            activity.runOnUiThread {
                if (files.isEmpty()) {
                    Toast.makeText(activity, "这些书还没有人物档案", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val uris = files.map {
                    Uri.parse("content://" + ExportProvider.AUTHORITY + "/" + Uri.encode(it.name))
                }
                val intent = if (uris.size == 1) {
                    Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
                } else {
                    Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    }
                }
                intent.type = "application/zip"
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching { activity.startActivity(Intent.createChooser(intent, "分享档案")) }
                exitSelection()
            }
        }.start()
    }

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(activity, "先勾选要删除的条目", Toast.LENGTH_SHORT).show()
            return
        }
        val rows = targets()
        if (rows.isEmpty()) return
        val hasLocal = rows.any { it.isLocal }
        AlertDialog.Builder(activity)
            .setTitle("删除 " + rows.size + " 本书的数据？")
            .setMessage(if (hasLocal) "会删除本地副本与该书的人物档案；原始文件不受影响。" else "会删除这本书的人物档案与阅读位置。")
            .setPositiveButton("删除") { _, _ ->
                val books = localStore.books()
                rows.forEach { t ->
                    if (t.isLocal) {
                        books.firstOrNull { it.fileName == t.fileName }?.let { localStore.remove(it) }
                    }
                    BookState.removeByName(activity, t.name)
                }
                exitSelection()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showHelp() {
        AlertDialog.Builder(activity)
            .setTitle("数据页说明")
            .setMessage(
                "【书目来源】\n" +
                    "· 标「已导入」的书来自本地 TXT/EPUB，可使用整本全文检索。\n" +
                    "· 标「接口书」的书只通过 legado 查询过，仅有其缓存章节。\n" +
                    "· 同名书的接口档案与本地档案会合并为一份，避免人物重复。\n\n" +
                    "【单人档案】\n" +
                    "· 最新概述：查询时生成的阶段性摘要。\n" +
                    "· 详细档案：传记式长文，按重要性限制篇幅。\n" +
                    "· 人物关系：以该人物为主人物生成的关系列表。\n" +
                    "· 出生位置与页码为估算值，可在悬浮面板「页码校准」中修正。\n\n" +
                    "【批量操作】\n" +
                    "长按任一条目进入多选，可导出为 TXT/Word 压缩包、通过系统分享，或删除 Eread 内的档案与本地副本；原始文件不受影响。\n\n" +
                    "【人名索引】\n" +
                    "在悬浮面板「页码校准 → 重建人名索引」可为整本书建立人名索引，用于区分「雷蒙」「雷蒙德三世」「雷蒙·杜·皮伊」这类名称。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }
}
