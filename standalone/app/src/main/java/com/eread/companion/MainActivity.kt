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

    private lateinit var libraryPage: LibraryPageView
    private lateinit var runPage: RunPageView
    private lateinit var bottomBar: View

    private var appliedTheme = ""

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.apply(this)
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 9001)
        }
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

    private fun buildRunPage(): RunPageView {
        runPage = RunPageView(
            this,
            store,
            localStore,
            onToggleOverlay = { toggleOverlay() },
            onImport = { pickFile() },
            onHelp = { showModeHelp() },
        )
        return runPage
    }

    fun stopProjection() {
        runCatching { stopService(Intent(this, ProjectionService::class.java)) }
    }

    fun authorizeProjection() {
        runCatching { startActivity(Intent(this, ProjectionActivity::class.java)) }
    }

    private fun refreshRun() {
        if (::runPage.isInitialized) runPage.refresh()
    }

    private val overlayReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            refreshRun()
        }
    }

    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(android.app.ActivityManager::class.java)
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == OverlayService::class.java.name }
    }

    private fun toggleOverlay() {
        if (isServiceRunning()) {
            stopService(Intent(this, OverlayService::class.java))
            runPage.refreshLater(300)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先在设置里授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            select(2)
            return
        }
        startService(Intent(this, OverlayService::class.java))
        runPage.refreshLater(500)
    }

    private fun showModeHelp() {
        AlertDialog.Builder(this)
            .setTitle("读取方式与截屏方式")
            .setMessage(
                "【Legado 模式】\n" +
                    "· 通过 legado 的 Web 服务（设置 → Web 服务，默认 1122 端口）读取书架、章节与阅读位置。\n" +
                    "· 位置自动同步，原文片段可点击跳转回 legado。\n" +
                    "· 不导入文件时，只能检索 legado 已缓存的章节。\n\n" +
                    "【Readest 模式】\n" +
                    "· 把同一本 EPUB/TXT 导入 Eread，使用本地全文检索，不依赖 legado。\n" +
                    "· Readest 没有本地接口，因此位置由「截屏识别」或手动选择章节得到。\n" +
                    "· 片段无法跳转，点击会复制原文。\n\n" +
                    "【截屏方式】\n" +
                    "· OCR识别：使用系统无障碍截屏，速度快、无状态栏图标，需在设置中开启「OCR识别权限」。\n" +
                    "· 屏幕录制：使用 MediaProjection，授权一次即可反复取帧，状态栏会显示「共享中」；重启手机后需重新授权。\n" +
                    "· 两种方式共用同一套识别、定位、页码校准与人名抽取流程，可各自开启 AI 辅助。\n\n" +
                    "Eread 不修改 legado 与 Readest 的任何数据。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    // ---------------- library page ----------------

    private fun buildLibraryPage(): LibraryPageView {
        libraryPage = LibraryPageView(this, store, localStore) { active ->
            bottomBar.visibility = if (active) View.GONE else View.VISIBLE
        }
        return libraryPage
    }

    private fun refreshLibrary() {
        if (::libraryPage.isInitialized) libraryPage.refresh()
    }

    private fun exitSelection() {
        if (::libraryPage.isInitialized) libraryPage.exitSelection()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (::libraryPage.isInitialized && libraryPage.isSelecting()) exitSelection() else super.onBackPressed()
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

    private fun buildSettingsPage(): View = SettingsPageView(this, store)

    private fun wrap(page: LinearLayout): View = ScrollView(this).apply {
        isFillViewport = true
        addView(page)
    }

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
