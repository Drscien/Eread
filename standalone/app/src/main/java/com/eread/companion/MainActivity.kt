package com.eread.companion

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var localStore: LocalStore
    private lateinit var content: FrameLayout
    private val tabs = ArrayList<LinearLayout>()
    private val icons = ArrayList<ImageView>()
    private val labels = ArrayList<TextView>()
    private val tiles = ArrayList<FrameLayout>()
    private val pages = ArrayList<View>()

    private lateinit var runPage: LinearLayout
    private lateinit var libraryPage: View
    private lateinit var libraryList: LinearLayout
    private lateinit var modeHttp: LinearLayout
    private lateinit var modeLocal: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var startButton: Button
    private lateinit var importHint: TextView
    private var selectionMode = false
    private val selected = HashSet<String>()
    private lateinit var bottomBar: View
    private lateinit var deleteBar: View

    private lateinit var port: EditText
    private lateinit var base: EditText
    private lateinit var key: EditText
    private lateinit var model: EditText
    private lateinit var perPage: EditText
    private var appliedTheme = ""
    private lateinit var libraryHelp: TextView
    private lateinit var libraryClose: TextView

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        store = Store(this)
        localStore = LocalStore(this)
        appliedTheme = store.theme

        val root = FrameLayout(this).apply { setBackgroundColor(Theme.bg) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        content = FrameLayout(this)
        column.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        bottomBar = buildBottomBar()
        column.addView(bottomBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)))

        root.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        deleteBar = buildDeleteBar()
        root.addView(deleteBar, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = dp(28) })
        deleteBar.visibility = View.GONE

        setContentView(root)

        runPage = buildRunPage()
        libraryPage = buildLibraryPage()
        pages.add(runPage)
        pages.add(libraryPage)
        pages.add(buildSettingsPage())
        pages.forEach { content.addView(it) }

        val filter = android.content.IntentFilter(OverlayService.ACTION_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(overlayReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(overlayReceiver, filter)
        }

        if (!store.helpShown) {
            store.helpShown = true
            window.decorView.postDelayed({
                if (!isFinishing && !isDestroyed) HelpDialog.show(this)
            }, 700)
        }

        val startTab = intent.getIntExtra("tab", -1)
        select(if (startTab in 0..2) startTab else store.tab.coerceIn(0, 2))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val t = intent.getIntExtra("tab", -1)
        if (t in 0..2) select(t)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(overlayReceiver) }
        super.onDestroy()
    }

    override fun onPause() {
        if (::port.isInitialized) saveSettings()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (store.theme != appliedTheme) {
            appliedTheme = store.theme
            recreate()
            return
        }
        refreshRun()
        refreshLibrary()
    }

    // ---------------- bottom bar ----------------

    private fun buildBottomBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Theme.bar)
        }
        addTab(bar, 0, R.drawable.ic_run, "运行")
        addTab(bar, 1, R.drawable.ic_library, "数据")
        addTab(bar, 2, R.drawable.ic_settings, "设置")
        val divider = View(this).apply { setBackgroundColor(Theme.divider) }
        val wrapper = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrapper.addView(divider, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        wrapper.addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(71)))
        return wrapper
    }

    private fun addTab(bar: LinearLayout, index: Int, iconRes: Int, label: String) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            isClickable = true
            setOnClickListener { select(index) }
        }
        val tile = FrameLayout(this).apply {
            background = rounded(0x00000000, dp(12))
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Theme.sub)
        }
        tile.addView(icon, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        val text = TextView(this).apply {
            this.text = label
            textSize = 11f
            setTextColor(Theme.sub)
        }
        item.addView(tile, LinearLayout.LayoutParams(dp(40), dp(40)))
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(2)
        item.addView(text, lp)

        bar.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        tabs.add(item)
        icons.add(icon)
        labels.add(text)
        tiles.add(tile)
    }

    private fun select(index: Int) {
        store.tab = index
        pages.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, _ ->
            val active = i == index
            icons[i].setColorFilter(if (active) Theme.accent else Theme.sub)
            labels[i].setTextColor(if (active) Theme.accent else Theme.sub)
            tiles[i].background = rounded(if (active) 0x1F7FB2FF else 0x00000000, dp(12))
        }
        if (index == 0) refreshRun()
        if (index == 1) refreshLibrary()
    }

    // ---------------- run page ----------------

    private fun buildRunPage(): LinearLayout {
        val page = page()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "人物速查"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "?"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Theme.sub)
            background = rounded(Theme.cardAlt, dp(11))
            setOnClickListener { showModeHelp() }
        }, LinearLayout.LayoutParams(dp(22), dp(22)))
        page.addView(header)

        page.addView(TextView(this).apply {
            text = "读取方式"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(14), 0, dp(6))
        })

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        modeHttp = modeCard("HTTP 模式", "legado 接口 · 已缓存章节")
        modeLocal = modeCard("本地文件", "TXT / EPUB · 整本可搜")
        val lp1 = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        lp1.rightMargin = dp(8)
        row.addView(modeHttp, lp1)
        row.addView(modeLocal, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(row)

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(Theme.sub)
            setPadding(0, dp(16), 0, 0)
        }
        page.addView(statusText)

        startButton = Button(this).apply {
            text = "启动悬浮窗"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rounded(Theme.accentStrong, dp(12))
            setOnClickListener { toggleOverlay() }
        }
        val blp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
        blp.topMargin = dp(20)
        page.addView(startButton, blp)

        page.addView(Button(this).apply {
            text = "导入本地文件"
            textSize = 14f
            setTextColor(Theme.text)
            background = rounded(Theme.cardAlt, dp(12))
            setOnClickListener { pickFile() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = dp(10) })

        importHint = TextView(this).apply {
            textSize = 12f
            setTextColor(Theme.faint)
            setPadding(0, dp(10), 0, 0)
        }
        page.addView(importHint)

        page.addView(TextView(this).apply {
            text = "悬浮球可拖动；长按球呼出「设置 / 停止」两个菜单球。查询结果按已读位置截断，不会剧透后文。"
            textSize = 12f
            setTextColor(Theme.faint)
            setPadding(0, dp(14), 0, 0)
        })
        return page
    }

    private fun modeCard(title: String, subtitle: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Theme.card, dp(12))
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(Theme.text)
        })
        card.addView(TextView(this).apply {
            text = subtitle
            textSize = 11f
            setTextColor(Theme.sub)
            setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private val overlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            refreshRun()
        }
    }

    private fun refreshRun() {
        val local = store.lastMode == "local"
        importHint.text = "已导入 " + localStore.books().size + " 本本地书；查询会自动优先使用本地全文。"
        val book = store.lastBook
        val chapter = store.lastChapter
        statusText.text = if (book.isBlank()) {
            "还没有查询记录。打开一本书，点悬浮球输入人名开始。"
        } else {
            "上次查询：《" + book + "》" + (if (chapter >= 0) " 第 " + (chapter + 1) + " 项" else "") +
                "\n当前命中方式：" + (if (local) "本地文件" else "legado 接口")
        }
        val running = isServiceRunning()
        startButton.text = if (running) "停止悬浮窗" else "启动悬浮窗"
        startButton.background = rounded(if (running) 0xFFC0453F.toInt() else Theme.accentStrong, dp(12))
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(android.app.ActivityManager::class.java)
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == OverlayService::class.java.name }
    }

    private fun toggleOverlay() {
        if (isServiceRunning()) {
            stopService(Intent(this, OverlayService::class.java))
            startButton.postDelayed({ refreshRun() }, 300)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先在设置里授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            select(2)
            return
        }
        startForegroundService(Intent(this, OverlayService::class.java))
        startButton.postDelayed({ refreshRun() }, 500)
    }

    private fun showModeHelp() {
        AlertDialog.Builder(this)
            .setTitle("两种读取方式")
            .setMessage(
                "HTTP 模式\n" +
                    "· 通过 legado 自带的 Web 服务（设置 → Web 服务，默认端口 1122）取书。\n" +
                    "· 不需要导入文件，但只能搜到已缓存（读过）的章节，冷门书回溯较浅。\n\n" +
                    "本地文件\n" +
                    "· 在「运行」页或「书库」导入 TXT / EPUB 后使用。\n" +
                    "· 整本可搜、速度更快、可离线；书名与 legado 书架一致时自动优先使用。\n" +
                    "· 跳转依赖章节标题匹配，标题对不上时落点会偏。\n\n" +
                    "两者都不改 legado 本身。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    // ---------------- library page ----------------

    private fun buildLibraryPage(): View {
        val page = page()
        val libHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        libHeader.addView(TextView(this).apply {
            text = "数据"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        libraryHelp = TextView(this).apply {
            text = "?"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Theme.sub)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), Theme.divider)
            }
            setOnClickListener { showLibraryHelp() }
        }
        libHeader.addView(libraryHelp, LinearLayout.LayoutParams(dp(22), dp(22)))
        libraryClose = TextView(this).apply {
            text = "×"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE87A9B.toInt())
            }
            visibility = View.GONE
            setOnClickListener { exitSelection() }
        }
        libHeader.addView(libraryClose, LinearLayout.LayoutParams(dp(22), dp(22)))
        page.addView(libHeader)
        page.addView(TextView(this).apply {
            text = "导入过的本地书、查过人物的书，都会在这里留下档案。"
            textSize = 12f
            setTextColor(Theme.sub)
            setPadding(0, dp(6), 0, dp(12))
        })
        libraryList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(libraryList)
        page.addView(TextView(this).apply {
            text = "在「运行」页导入新文件"
            textSize = 12f
            setTextColor(Theme.faint)
            setPadding(0, dp(16), 0, 0)
        })
        page.isClickable = true
        page.setOnClickListener { if (selectionMode) exitSelection() }
        val scrolled = wrap(page)
        scrolled.isClickable = true
        scrolled.setOnClickListener { if (selectionMode) exitSelection() }
        return scrolled
    }

    private data class DataRow(
        val key: String,
        val name: String,
        val subtitle: String,
        val isLocal: Boolean,
        val bookUrl: String,
        val fileName: String,
    )

    private fun libraryRows(): List<DataRow> {
        val locals = localStore.books()
        val states = runCatching { BookState.all(this) }.getOrDefault(emptyList())
        val localNames = locals.map { it.name }.toSet()
        val rows = ArrayList<DataRow>()
        locals.forEach { b ->
            val st = states.firstOrNull { it.bookName == b.name }
            val cards = st?.cards?.size ?: 0
            val extra = if (cards > 0) " · " + cards + " 个人物" else ""
            rows.add(DataRow(
                "L:" + b.fileName,
                b.name,
                b.chapters.size.toString() + " 节 · 已导入" + extra,
                true,
                st?.bookUrl ?: "",
                b.fileName,
            ))
        }
        states.filter { it.bookName !in localNames }
            .filter { it.cards.isNotEmpty() || it.latestPos > 0 }
            .sortedByDescending { st -> st.cards.values.maxOfOrNull { c -> c.updatedAt } ?: 0L }
            .forEach { st ->
                rows.add(DataRow(
                    "S:" + st.bookUrl,
                    st.bookName,
                    "接口书 · " + st.cards.size + " 个人物",
                    false,
                    st.bookUrl,
                    "",
                ))
            }
        return rows
    }

    private fun openBookData(row: DataRow) {
        startActivity(Intent(this, DataActivity::class.java)
            .putExtra("bookUrl", row.bookUrl)
            .putExtra("bookName", row.name))
    }

    private fun refreshLibrary() {
        libraryList.removeAllViews()
        val rows = libraryRows()
        if (rows.isEmpty()) {
            libraryList.addView(TextView(this).apply {
                text = "还没有记录。导入本地文件，或在阅读时查一次人物。"
                textSize = 13f
                setTextColor(Theme.faint)
                setPadding(0, dp(20), 0, dp(20))
            })
            return
        }
        rows.forEach { rowData ->
            val isSel = selected.contains(rowData.key)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(if (isSel) Theme.cardSel else Theme.card, dp(10))
                isClickable = true
                setOnClickListener {
                    if (selectionMode) setSelected(rowData.key, !selected.contains(rowData.key)) else openBookData(rowData)
                }
                setOnLongClickListener {
                    if (!selectionMode) enterSelection(rowData.key)
                    true
                }
            }
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply {
                text = rowData.name
                textSize = 14f
                setTextColor(Theme.text)
            })
            info.addView(TextView(this).apply {
                text = rowData.subtitle
                textSize = 11f
                setTextColor(Theme.sub)
                setPadding(0, dp(3), 0, 0)
            })
            row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (selectionMode) {
                val cb = CheckBox(this).apply {
                    isChecked = isSel
                    buttonTintList = ColorStateList.valueOf(Theme.accent)
                    setOnCheckedChangeListener { _, checked -> setSelected(rowData.key, checked) }
                }
                row.addView(cb, LinearLayout.LayoutParams(dp(40), dp(40)))
            } else {
                row.addView(TextView(this).apply {
                    text = "档案"
                    textSize = 12f
                    setTextColor(Theme.accent)
                    setPadding(dp(10), dp(6), dp(10), dp(6))
                    setOnClickListener { openBookData(rowData) }
                })
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(8)
            libraryList.addView(row, lp)
        }
    }

    private fun setSelected(key: String, on: Boolean) {
        if (on) selected.add(key) else selected.remove(key)
        refreshLibrary()
    }

    private fun enterSelection(key: String) {
        selectionMode = true
        libraryHelp.visibility = View.GONE
        libraryClose.visibility = View.VISIBLE
        selected.clear()
        selected.add(key)
        bottomBar.visibility = View.GONE
        showDeleteBar()
        refreshLibrary()
    }

    private fun exitSelection() {
        selectionMode = false
        if (::libraryHelp.isInitialized) libraryHelp.visibility = View.VISIBLE
        if (::libraryClose.isInitialized) libraryClose.visibility = View.GONE
        selected.clear()
        bottomBar.visibility = View.VISIBLE
        hideDeleteBar()
        refreshLibrary()
    }

    private fun buildDeleteBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        bar.addView(circleButton(R.drawable.ic_export, Theme.accentStrong) { exportSelected() })
        bar.addView(circleButton(R.drawable.ic_share, 0xFF3FA46A.toInt()) { shareSelected() })
        bar.addView(circleButton(R.drawable.ic_delete, 0xFFC0453F.toInt()) { deleteSelected() })
        return bar
    }

    private fun circleButton(iconRes: Int, color: Int, onClick: () -> Unit): View {
        val wrap = LinearLayout(this).apply {
            gravity = Gravity.CENTER
        }
        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }
            elevation = dp(8).toFloat()
            setOnClickListener { onClick() }
        }
        circle.addView(ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER))
        wrap.addView(circle, LinearLayout.LayoutParams(dp(58), dp(58)))
        return wrap.also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun shareSelected() {
        val targets = libraryRows().filter { selected.contains(it.key) }
        if (targets.isEmpty()) {
            Toast.makeText(this, "先勾选要分享的条目", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("分享 " + targets.size + " 本书的档案")
            .setItems(arrayOf("TXT 文本", "Word 文档（.docx）")) { _, which ->
                val format = if (which == 0) BookExporter.Format.TXT else BookExporter.Format.DOCX
                doShare(targets, format)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doShare(rows: List<DataRow>, format: BookExporter.Format) {
        Toast.makeText(this, "正在打包…", Toast.LENGTH_SHORT).show()
        Thread {
            val states = BookState.all(this).filter { st -> rows.any { it.name == st.bookName } }
            val files = runCatching { BookExporter.exportToCache(this, states, format) }.getOrDefault(emptyList())
            runOnUiThread {
                if (files.isEmpty()) {
                    Toast.makeText(this, "这些书还没有人物档案", Toast.LENGTH_SHORT).show()
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
                runCatching { startActivity(Intent.createChooser(intent, "分享档案")) }
                exitSelection()
            }
        }.start()
    }

    private fun exportSelected() {
        val targets = libraryRows().filter { selected.contains(it.key) }
        if (targets.isEmpty()) {
            Toast.makeText(this, "先勾选要导出的条目", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("导出 " + targets.size + " 本书的档案")
            .setItems(arrayOf("TXT 文本", "Word 文档（.docx）")) { _, which ->
                val format = if (which == 0) BookExporter.Format.TXT else BookExporter.Format.DOCX
                doExport(targets, format)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doExport(rows: List<DataRow>, format: BookExporter.Format) {
        Toast.makeText(this, "正在导出…", Toast.LENGTH_SHORT).show()
        Thread {
            val states = BookState.all(this).filter { st -> rows.any { it.name == st.bookName } }
            val paths = runCatching { BookExporter.export(this, states, format) }.getOrDefault(emptyList())
            runOnUiThread {
                Toast.makeText(
                    this,
                    if (paths.isEmpty()) "导出失败：这些书还没有人物档案" else "已导出 " + paths.size + " 个压缩包到 Download/Eread",
                    Toast.LENGTH_LONG
                ).show()
                if (paths.isNotEmpty()) exitSelection()
            }
        }.start()
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

    private fun deleteSelected() {
        if (selected.isEmpty()) {
            Toast.makeText(this, "先勾选要删除的条目", Toast.LENGTH_SHORT).show()
            return
        }
        val targets = libraryRows().filter { selected.contains(it.key) }
        if (targets.isEmpty()) return
        val hasLocal = targets.any { it.isLocal }
        AlertDialog.Builder(this)
            .setTitle("删除 " + targets.size + " 本书的数据？")
            .setMessage(if (hasLocal) "会删除本地副本与该书的人物档案；原始文件不受影响。" else "会删除这本书的人物档案与阅读位置。")
            .setPositiveButton("删除") { _, _ ->
                val books = localStore.books()
                targets.forEach { t ->
                    if (t.isLocal) {
                        books.firstOrNull { it.fileName == t.fileName }?.let { localStore.remove(it) }
                    }
                    BookState.removeByName(this, t.name)
                }
                exitSelection()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (selectionMode) exitSelection() else super.onBackPressed()
    }

    private fun showLibraryHelp() {
        AlertDialog.Builder(this)
            .setTitle("数据能做什么")
            .setMessage(
                "这里记录两类书：导入过 TXT / EPUB 的本地书，和只用接口查过人物的书。\n\n" +
                "本地书标「已导入」，查询时会优先用整本全文；接口书只有已缓存章节。\n\n" +
                "长按任一条目进入多选，可批量删除 Eread 里的数据；原始文件不受影响。\n\n" +
                "点条目或「档案」可以看这本书的人物档案、阅读位置与页码估算。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun pickFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/epub+zip", "application/octet-stream"))
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val name = queryName(uri) ?: "未命名.txt"
        Toast.makeText(this, "正在导入 " + name, Toast.LENGTH_SHORT).show()
        Thread {
            runCatching {
                val input = contentResolver.openInputStream(uri) ?: error("打不开文件")
                val book = BookImporter.import(this, name, input)
                localStore.save(book)
            }.onSuccess {
                runOnUiThread {
                    refreshLibrary()
                    Toast.makeText(this, "导入完成", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                runOnUiThread { Toast.makeText(this, "导入失败：" + it.message, Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    private fun queryName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------------- settings page ----------------

    private fun buildSettingsPage(): View {
        val page = page()
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "设置"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        page.addView(header)

        fun field(value: String): EditText = EditText(this).apply {
            setText(value)
            textSize = 13f
            gravity = Gravity.END
            setTextColor(Theme.text)
            setHintTextColor(Theme.faint)
            background = null
            setPadding(0, dp(4), 0, dp(4))
            isSingleLine = true
        }

        port = field(store.legadoPort.toString())
        base = field(store.apiBase)
        key = field(store.apiKey).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        model = field(store.model)
        perPage = field(store.charsPerPage.toString())

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

        page.addView(sectionTitle("外观"))
        val lookCard = card()
        lookCard.addView(arrowRow("主题", if (store.theme == "light") "浅色" else "深色") {
            startActivity(Intent(this@MainActivity, ThemeActivity::class.java))
        })
        page.addView(lookCard)

        page.addView(sectionTitle("数据"))
        val dataCard = card()
        dataCard.addView(arrowRow("Token 消耗", usageSummary()) {
            startActivity(Intent(this@MainActivity, UsageActivity::class.java))
        })
        page.addView(dataCard)

        page.addView(sectionTitle("悬浮窗"))
        val winCard = card()
        winCard.addView(arrowRow(
            "悬浮窗权限",
            if (Settings.canDrawOverlays(this)) "已授权" else "未授权"
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName)))
        })
        page.addView(winCard)

        page.addView(sectionTitle("关于"))
        val aboutCard = card()
        aboutCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_info)
                setColorFilter(Theme.sub)
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) })
            addView(TextView(this@MainActivity).apply {
                text = "关于"
                textSize = 13f
                setTextColor(Theme.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "›"
                textSize = 18f
                setTextColor(Theme.faint)
            })
        })
        aboutCard.addView(View(this).apply { setBackgroundColor(Theme.divider) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        aboutCard.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { HelpDialog.show(this@MainActivity) }
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.ic_help)
                setColorFilter(Theme.sub)
            }, LinearLayout.LayoutParams(dp(20), dp(20)).apply { rightMargin = dp(10) })
            addView(TextView(this@MainActivity).apply {
                text = "帮助"
                textSize = 13f
                setTextColor(Theme.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = "›"
                textSize = 18f
                setTextColor(Theme.faint)
            })
        })
        page.addView(aboutCard)

        autoSave(port, base, key, model, perPage)

        page.addView(TextView(this).apply {
            text = "Eread 0.1 · 不修改 legado，通过其 Web 服务或本地文件取书"
            textSize = 10.5f
            setTextColor(Theme.faint)
            setPadding(dp(2), dp(18), 0, 0)
        })
        return wrap(page)
    }

    private fun wrap(page: LinearLayout): View = ScrollView(this).apply {
        isFillViewport = true
        addView(page)
    }

    private fun levelRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        row.addView(TextView(this).apply {
            text = "推理等级"
            textSize = 13f
            setTextColor(Theme.text)
        })
        val chips = LinearLayout(this).apply {
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
            val tv = TextView(this).apply {
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
        val usage = UsageStore(this)
        val totals = longArrayOf(0, 0, 0)
        usage.byFeature().values.forEach { v -> totals[0] += v[0]; totals[1] += v[1]; totals[2] += v[2] }
        val cost = (totals[0] * store.priceInput + totals[1] * store.priceOutput) / 1_000_000.0
        return String.format("¥%.3f", cost)
    }

    private fun sectionTitle(t: String): TextView = TextView(this).apply {
        text = t
        textSize = 11f
        setTextColor(Theme.faint)
        setPadding(dp(4), dp(18), 0, dp(6))
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(Theme.card, dp(14))
        setPadding(dp(14), dp(4), dp(14), dp(4))
    }

    private fun divider(): View = View(this).apply {
        setBackgroundColor(Theme.divider)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun inlineRow(title: String, edit: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(6), 0, dp(6))
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 13f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(edit, LinearLayout.LayoutParams(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun arrowRow(title: String, value: String, onClick: () -> Unit): LinearLayout =
        rowWithRight(title, value, true, false, onClick)

    private fun rowWithRight(
        title: String,
        value: String,
        showChevron: Boolean,
        danger: Boolean,
        onClick: () -> Unit,
    ): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 13f
                setTextColor(if (danger) 0xFFE57373.toInt() else Theme.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@MainActivity).apply {
                text = value
                textSize = 12f
                setTextColor(Theme.faint)
            })
            if (showChevron) {
                addView(TextView(this@MainActivity).apply {
                    text = "›"
                    textSize = 18f
                    setTextColor(Theme.faint)
                    setPadding(dp(8), 0, 0, 0)
                })
            }
        }

    private fun saveSettings() {
        store.legadoPort = port.text.toString().trim().toIntOrNull() ?: 1122
        store.apiBase = base.text.toString().trim()
        store.apiKey = key.text.toString().trim()
        store.model = model.text.toString().trim()
        store.charsPerPage = perPage.text.toString().trim().toIntOrNull() ?: 330
    }

    private fun autoSave(vararg edits: EditText) {
        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (::port.isInitialized && ::base.isInitialized && ::key.isInitialized &&
                    ::model.isInitialized && ::perPage.isInitialized
                ) {
                    saveSettings()
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
        }
        edits.forEach { it.addTextChangedListener(watcher) }
    }

    private fun calibratePage() {
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "例如 52（52/102 里的 52）"
        }
        AlertDialog.Builder(this)
            .setTitle("页码校准")
            .setMessage("打开阅读器当前这一章，看底部显示的页码，填进来。同章内的片段页码会据此校准。")
            .setView(input)
            .setPositiveButton("校准") { _, _ ->
                val pageNo = input.text.toString().trim().toIntOrNull()
                if (pageNo == null || pageNo <= 1) {
                    Toast.makeText(this, "页码要大于 1", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                Thread {
                    runCatching {
                        val book = LegadoApi(store.legadoPort).currentBook() ?: error("拿不到当前书")
                        PageEstimator.calibrate(store, book.durChapterIndex, book.durChapterPos, pageNo)
                            ?: error("位置或页码不合理")
                    }.onSuccess { cpp ->
                        runOnUiThread {
                            perPage.setText(store.charsPerPage.toString())
                            Toast.makeText(this, "已校准：" + cpp.toInt() + " 字 / 页", Toast.LENGTH_LONG).show()
                        }
                    }.onFailure {
                        runOnUiThread { Toast.makeText(this, "校准失败：" + (it.message ?: "未知"), Toast.LENGTH_LONG).show() }
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ---------------- helpers ----------------

    private fun page(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(24), dp(20), dp(16))
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }

    companion object {
        private const val REQ_PICK = 1001
    }
}
