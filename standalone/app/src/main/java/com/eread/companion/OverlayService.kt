package com.eread.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import org.json.JSONObject
import android.graphics.Bitmap
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.withContext
import kotlin.math.abs

class OverlayService : Service() {


    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private lateinit var localStore: LocalStore
    private lateinit var ocrController: OcrController
    private lateinit var relationController: RelationController
    private lateinit var session: ReadingSession
    private lateinit var queryController: QueryController
    private lateinit var ballWindow: BallWindow
    private var panel: PanelView? = null
    private var lastOcrNames: List<String> = emptyList()

    private val genericNames = setOf(
        "国王", "王后", "太后", "太后党", "权贵", "贵族", "骑士", "团长", "大团长",
        "教皇", "苏丹", "皇帝", "伯爵", "公爵", "将军", "首相", "摄政", "主教",
        "家族", "兄弟", "儿子", "父亲", "母亲", "妻子", "女儿", "他们", "她们",
        "我们", "你们", "本人", "众人", "国王们", "诸位",
    )

    private fun cleanNameToken(raw: String): String? {
        var text = raw.trim().trim('·', '-', '—', '*', '。', '，', '、', ' ', '"', '\'', '「', '」', '：', ':')
        text = text.replace(Regex("^(以及|与|和|及|同|跟)"), "")
        text = text.replace(
            Regex("^(法王|英王|德皇|教皇|苏丹|国王|王后|太后|伯爵|公爵|大主教|主教|摄政王|摄政|大团长|团长)"),
            "",
        )
        text = NameParser.cleanDescriptor(text)
        if (text.length !in store.nameMinLength..store.nameMaxLength) return null
        val extra = store.nameStopWords.split(',', '，', '、', ' ', '\n')
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (text in genericNames || text in extra) return null
        if (text.none { it.code in 0x4e00..0x9fff } && !text.matches(Regex("[A-Za-z][A-Za-z .'\\-]*"))) return null
        return text
    }

    /** 容忍编号、项目符号、逗号分隔等模型输出差异。 */
    private fun parseOcrNames(raw: String): List<String> = raw.lines()
        .flatMap { line -> line.split('、', '，', ',', '；', ';') }
        .map { it.replace(Regex("^\\d+[.、)]\\s*"), "") }
        .mapNotNull { cleanNameToken(it) }
        .filter { it.none { c -> c.isDigit() } }
        .distinct()
        .take(10)

    /** 用已建人名索引反向匹配本页文字，保证常见人物一定出现。 */
    private fun knownNames(local: LocalBook, text: String): List<String> {
        val index = runCatching { NameIndex(NameIndexStore.load(this, local)) }.getOrDefault(NameIndex(emptyList()))
        return index.entities
            .filter { e ->
                text.contains(e.full) ||
                    (e.base.length >= 3 && text.contains(e.base)) ||
                    (e.latin.length >= 4 && text.contains(e.latin))
            }
            .sortedByDescending { it.count }
            .map { it.full }
            .distinct()
            .take(8)
    }

    /** 模型不可用时的兜底：只收 ·结构、世次和多词拉丁名。 */
    private fun localNames(text: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("[\\u4e00-\\u9fff]{1,6}(?:·[\\u4e00-\\u9fff]{1,5}){1,3}(?:[一二三四五六七八九十]{1,3}世)?")
            .findAll(text).take(6).forEach { out.add(NameParser.cleanDescriptor(it.value)) }
        Regex("([\\u4e00-\\u9fff]{2,5})(十[一二]?|[一二三四五六七八九十])世")
            .findAll(text).take(6).forEach { out.add(it.groupValues[1] + it.groupValues[2] + "世") }
        Regex("[A-Z][a-z]{2,}(?:\\s+(?:de|du|di|da|van|von|of)?\\s*[A-Z][a-z]{2,}){1,3}")
            .findAll(text).take(6).forEach { out.add(it.value) }
        return out.mapNotNull { cleanNameToken(it) }.distinct().take(8)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Handler(Looper.getMainLooper())
    private var job: Job? = null

    private var lastBook: BookInfo? = null
    private var lastName: String = ""
    private var lastUi: UiState? = null

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Theme.apply(this)
        store = Store(this)
        localStore = LocalStore(this)
        ocrController = OcrController(this, store, localStore)
        relationController = RelationController(this, store, localStore)
        session = ReadingSession(this, store, localStore)
        queryController = QueryController(
            this,
            store,
            localStore,
            session,
            onState = { state -> render(state) },
            onBook = { book, name ->
                lastBook = book
                lastName = name
                panel?.setQuery(name)
            },
        )
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        ballWindow = BallWindow(
            this,
            store,
            onTap = { expand() },
            onSettings = { openSettingsTab() },
            onStop = { stopSelf() },
        )
        ballWindow.show()
    }

    private fun startAsForeground() {
        val channel = "eread"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel(channel, "悬浮窗", NotificationManager.IMPORTANCE_LOW))
        }
        val n: Notification = Notification.Builder(this, channel)
            .setContentTitle("Eread")
            .setContentText("人物速查悬浮窗运行中")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName))
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun expand() {
        if (panel != null) return
        Theme.apply(this)
        ballWindow.hide()
        val p = PanelView(
            this,
            onSubmit = { name -> onSubmit(name, false) },
            onClose = { collapse() },
            onJump = { hit -> onJump(hit) },
            pageOfHit = { chapter, pos -> PageEstimator.estimate(store, session.currentBookKey(), session.sourceKey(), chapter, pos) },
            onRegenerate = { if (lastName.isNotBlank()) onSubmit(lastName, true) },
            onSetLatest = { setLatest() },
            onAliasMerge = { mergeAlias() },
            onCalibrate = { page -> calibratePage(page) },
            onIdentify = { identifyPage() },
            onOcrCalibrate = { ocrCalibrate() },
            onPickChapter = { showChapterPicker() },
            onRebuildIndex = { rebuildNameIndex() },
            onReadestOcr = { readestOcr() },
            onRelPickMain = { pickRelationMain() },
            onRelPickSecondary = { pickRelationSecondary() },

            onRelGenerate = { main, secs -> generateRelation(main, secs) },
            onRelTimeline = { showRelationTimeline() },
        )
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val panelW = minOf(dp(340), screenW - dp(24))
        val lp = WindowManager.LayoutParams(
            panelW, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenW - panelW - dp(8)
            y = dp(96)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }
        attachPanelDrag(p, lp)
        wm.addView(p, lp)
        panel = p
        p.setSource(store.source)
        runCatching { p.setRecentNames(store.recentSearches(store.lastBook.ifBlank { store.manualBook })) }
        runCatching {
            val lb = session.boundLocalBook()
            val rp = lb?.let { store.readestPos(it.name) }
            if (lb != null && rp != null) {
                p.setChapterLabel("当前章节：第 " + (rp.first + 1) + " 项 " + session.chapterTitleOf(lb, rp.first))
            }
        }
        val cached = lastUi
        if (lastName.isNotBlank()) p.setQuery(lastName)
        if (cached is UiState.Done) {
            p.render(cached)
        } else {
            p.render(UiState.Idle)
        }
        p.setBookLabel(if (store.lastBook.isNotBlank()) "《" + store.lastBook + "》" else "正在识别当前书籍…")
        scope.launch {
            val b = withContext(Dispatchers.IO) { runCatching { LegadoApi(store.legadoPort).currentBook() }.getOrNull() }
            if (b != null) {
                store.lastBook = b.name
                store.lastChapter = b.durChapterIndex
                if (panel === p) p.setBookLabel("《" + b.name + "》 第 " + (b.durChapterIndex + 1) + " 项")
            }
        }
        p.post { p.focusInput() }
    }

    private fun attachPanelDrag(p: PanelView, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        p.headerView.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > dp(4) || abs(dy) > dp(4))) dragging = true
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(p, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun openSettingsTab() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra("tab", 2)
        runCatching { startActivity(intent) }
    }

    private fun collapse() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        ballWindow.show()
    }

    private fun render(state: UiState) {
        if (state is UiState.Done || state is UiState.Error) lastUi = state
        main.post { panel?.render(state) }
    }

    private fun onSubmit(name: String, force: Boolean) {
        job?.cancel()
        job = scope.launch {
            // 静默校准会额外截屏，导致点击人名时悬浮窗闪烁；改为用户主动校准
            queryController.run(name, force)
        }
    }

    private suspend fun aiMatchChapter(
        local: LocalBook,
        ocr: String,
        ranked: List<Locator.Hit>,
    ): Locator.Hit? {
        if (ranked.isEmpty()) return null
        return runCatching {
            val sb = StringBuilder()
            sb.append("下面是 OCR 识别出的一页小说正文，可能有个别错字：\n")
            sb.append(ocr.take(900)).append("\n\n")
            sb.append("下面是本地书中若干候选章节的片段，编号从 0 开始：\n")
            ranked.forEachIndexed { i, h ->
                val text = localStore.chapterText(local, h.chapter).orEmpty()
                val start = (h.pos - 200).coerceAtLeast(0)
                val end = (start + 1600).coerceAtMost(text.length)
                sb.append("[").append(i).append("] ")
                sb.append(local.chapters[h.chapter].title).append("\n")
                sb.append(text.substring(start, end)).append("\n\n")
            }
            sb.append("请判断 OCR 正文对应哪个候选章节；若都不匹配，index 返回 -1。\n")
            sb.append("若匹配，从该候选片段中逐字抄出与 OCR 正文开头对应的 20–30 个字作为 quote，必须与片段原文完全一致。\n")
            sb.append("只输出 JSON：{\"index\":0,\"quote\":\"...\"}")
            val out = Llm(store, UsageStore(this)).complete(sb.toString(), "AI定位", 500)
            val startJson = out.indexOf('{')
            val endJson = out.lastIndexOf('}')
            if (startJson < 0 || endJson <= startJson) return null
            val obj = JSONObject(out.substring(startJson, endJson + 1))
            val idx = obj.optInt("index", -1)
            if (idx < 0 || idx >= ranked.size) return null
            val quote = Locator.normalize(obj.optString("quote"))
            if (quote.length < 8) return null
            val ch = ranked[idx].chapter
            val text = Locator.normalize(localStore.chapterText(local, ch).orEmpty())
            var at = text.indexOf(quote)
            if (at < 0) at = text.indexOf(quote.take(12))
            if (at < 0) return null
            Locator.Hit(ch, at.coerceAtLeast(0), 0.9)
        }.getOrNull()
    }

    private suspend fun captureScreenText(): String {
        val hadPanel = panel != null
        if (hadPanel) {
            panel?.let { runCatching { wm.removeView(it) } }
            panel = null
        }
        ballWindow.hide()
        delay(350)
        val text = ocrController.captureText()
        if (hadPanel) expand()
        return text
    }

    private fun readestOcr() {
        val local = session.boundLocalBook()
        if (local == null) {
            panel?.setSearchStatus("这本书还没导入：先在主页导入同一本 EPUB")
            android.widget.Toast.makeText(this, "当前书未导入，无法识别", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        if (store.captureMode != "projection") {
            ocrController.readyError()?.let { error ->
                panel?.setSearchStatus(error)
                android.widget.Toast.makeText(this, error, android.widget.Toast.LENGTH_LONG).show()
                return
            }
        }
        if (store.captureMode == "projection" && !ProjectionService.ready()) {
            requestProjectionAndResume()
            return
        }
        job?.cancel()
        panel?.setOcrBusy(true)
        job = scope.launch {
            try {
                runReadestOcr(local)
            } finally {
                panel?.setOcrBusy(false)
            }
        }
    }

    private var authWait: Job? = null
    private var reverseJob: Job? = null

    /** 未授权时先请求录屏，授权成功后自动重跑刚才那次识别。 */
    private fun requestProjectionAndResume() {
        authWait?.cancel()
        panel?.setSearchStatus("正在请求录屏授权…")
        authWait = scope.launch {
            ocrController.requestProjectionPrompt()
            val deadline = System.currentTimeMillis() + 120_000
            while (!ProjectionService.ready() && System.currentTimeMillis() < deadline) {
                delay(300)
            }
            if (ProjectionService.ready()) {
                ProjectionPrompter.cancel(this@OverlayService)
                panel?.setSearchStatus("录屏已授权，继续识别…")
                delay(400)
                readestOcr()
            } else {
                panel?.setSearchStatus("录屏授权未完成，可再次点击识别")
            }
        }
    }

    private suspend fun runReadestOcr(local: LocalBook) {
        run {
            val text = captureScreenText()
            if (text.isBlank()) {
                panel?.setSearchStatus(ocrController.lastError ?: "没取到屏幕文字，靠近正文再试")
                return
            }
            val hint = store.readestPos(local.name)?.first ?: 0
            val located = ocrController.locate(local, text, hint, allowAi = true)
            val hit = located.hit
            val chars = Locator.normalize(text).length
            var posNote = ""
            if (hit != null) {
                store.setReadestPos(local.name, hit!!.chapter, hit!!.pos)
                store.manualChapter = hit!!.chapter
                // 当前位置取“本页末尾”，否则按页起点截断会把当前页的文字排除在检索之外
                store.manualPos = hit!!.pos + chars
                if (chars > 80) store.cppReadest = chars
                posNote = "已定位：" + session.chapterTitleOf(local, hit!!.chapter) + "（" + (hit!!.score * 100).toInt() + "%）"
                panel?.setBookLabel("《" + local.name + "》 " + session.chapterTitleOf(local, hit!!.chapter) + " · OCR")
                panel?.setChapterLabel("当前章节：第 " + (hit!!.chapter + 1) + " 项 " + session.chapterTitleOf(local, hit!!.chapter))
            } else {
                posNote = "未定位到章节 · 本屏约 " + chars + " 字"
            }
            val fixedText = located.text.ifBlank { text }
            val namesSource = if (fixedText.length > 1200) {
                fixedText.take(1200) + "\n" + fixedText.takeLast(400)
            } else fixedText
            val prompt = "下面是小说的当前页正文。只提取这一页里出现的人物称呼，每行一个，最多 10 个。" +
                "不要解释、不要编号、不要标点，不要输出不是人名的词。" +
                "外国人名中的「·」和末尾的「三世/四世」必须保留，不要拆分或删除。\n\n" + namesSource.take(1200)
            val out = runCatching {
                Llm(store, UsageStore(this@OverlayService)).complete(prompt, "快速识别", 300, applyStyle = false)
            }.getOrElse {
                panel?.setSearchStatus("OCR 完成，但模型调用失败：" + (it.message ?: "未知"))
                return
            }
            val known = withContext(Dispatchers.IO) { knownNames(local, namesSource) }
            val names = (parseOcrNames(out) + localNames(namesSource) + known).distinct().take(10)
            runCatching {
                java.io.File(filesDir, "last_ocr.txt").writeText(
                    "chapter=" + (hit?.chapter ?: -1) + " pos=" + (hit?.pos ?: -1) +
                        " score=" + (hit?.score ?: 0.0) + " manualPos=" + store.manualPos +
                        " chars=" + chars + "\nnames=" + names.joinToString("、") + "\n\n" + text
                )
            }
            lastOcrNames = names
            store.setOcrNames(local.name, names)
            panel?.setRecentNames(names)
            panel?.setSearchStatus(
                posNote + " · 人名 " + names.size + " 个" +
                    if (names.isEmpty() && out.isNotBlank()) "（模型输出：" + out.take(40) + "）" else " · 点名字直接去查"
            )
            if (hit != null) updateReadestPosition(local, hit!!)
        }
    }

    private fun updateReadestPosition(local: LocalBook, hit: Locator.Hit) {
        val state = BookState.load(this, "local://" + local.fileName, local.name)
        val global = hit.chapter * 100000 + hit.pos
        if (global < state.latestPos - 300) {
            state.currentPos = global
            state.rereads.add(RereadEntry(lastName.ifBlank { store.manualBook }, 0, "", System.currentTimeMillis()))
        } else {
            state.currentPos = global
            if (global > state.latestPos) state.latestPos = global
        }
        state.save(this)
    }

    private fun relationCandidates(): List<Triple<String, String, String>> =
        relationController.candidates(session.boundLocalBook(), lastOcrNames)

    private fun ensureProfiles(local: LocalBook, names: List<String>) =
        relationController.ensureProfiles(local, names)

    private fun pickRelationMain() {
        val candidates = relationCandidates()
        if (candidates.isEmpty()) {
            panel?.setRelationResult("这本书还没有人物档案")
            return
        }
        val labels = candidates.map { c ->
            c.first + (if (c.third.isNotBlank()) "（" + c.third + "）" else "") + " · " + c.second
        }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("选择主人物")
            .setItems(labels.toTypedArray()) { _, which ->
                panel?.setRelationMain(candidates[which].first)
            }
            .setNegativeButton("取消", null)
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun pickRelationSecondary() {
        val main = panel?.relationMain().orEmpty()
        val candidates = relationCandidates().filter { it.first != main }
        if (candidates.isEmpty()) {
            // 只有主人物时也给出明确说明，而不是点击无反应
            android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("选择次人物")
                .setMessage("除主人物「" + main + "」外，这本书暂时没有其他人物。\n\n" +
                    "可以先在阅读页点「截屏识别当前页」，或在「人物速查」里查询一个人物，再回来选择。")
                .setPositiveButton("知道了", null)
                .create().apply {
                    window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    show()
                }
            return
        }
        val names = candidates.map { it.first }
        val current = panel?.relationSelected() ?: emptyList()
        val checked = BooleanArray(names.size) { names[it] in current }
        val labels = candidates.map { c ->
            c.first + (if (c.third.isNotBlank()) "（" + c.third + "）" else "") + " · " + c.second
        }
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("选择次人物（档案 / 当前页）")
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("确定") { _, _ ->
                val picked = names.filterIndexed { i, _ -> checked[i] }
                val local = session.boundLocalBook()
                if (local != null) ensureProfiles(local, picked)
                panel?.addRelationSecondary(picked)
            }
            .setNegativeButton("取消", null)
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun showRelationTimeline() {
        val main = panel?.relationMain().orEmpty()
        if (main.isBlank()) {
            panel?.setRelationResult("先选择主人物")
            return
        }
        val local = session.boundLocalBook()
        if (local == null) {
            panel?.setRelationResult("先在页码校准里选择本书")
            return
        }
        val state = BookState.load(this, "local://" + local.fileName, local.name)
        val card = state.cards[main]
        val body = card?.relations?.takeIf { it.isNotBlank() } ?: "还没有生成过关系列表。"
        android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("关系时间线 · " + main)
            .setMessage(body)
            .setPositiveButton("关闭", null)
            .create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun generateRelation(main: String, seconds: List<String>) {
        val local = session.boundLocalBook()
        if (local == null) {
            panel?.setRelationResult("先在页码校准里选择本书")
            return
        }
        job?.cancel()
        job = scope.launch {
            panel?.setRelationBusy(true)
            panel?.setRelationResult("生成中…")
            try {
            android.widget.Toast.makeText(this@OverlayService, "开始生成关系列表…", android.widget.Toast.LENGTH_SHORT).show()
            if (store.apiKey.isBlank()) {
                panel?.setRelationResult("还没有配置 API Key，先去设置里填写")
                android.widget.Toast.makeText(this@OverlayService, "缺少 API Key", android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            relationController.ensureSummaries(local, listOf(main) + seconds)
            val pairs = relationController.cooccurrences(local, main, seconds)
            val state = BookState.load(this@OverlayService, "local://" + local.fileName, local.name)
            val prompt = relationController.buildPrompt(local, main, seconds, pairs)
            val out = runCatching {
                Llm(store, UsageStore(this@OverlayService)).complete(prompt, "关系网", 900)
            }.getOrElse {
                panel?.setRelationResult("生成失败：" + (it.message ?: "未知"))
                android.widget.Toast.makeText(this@OverlayService, "生成失败：" + (it.message ?: "未知"), android.widget.Toast.LENGTH_SHORT).show()
                return@launch
            }
            val text = filterRelations(out, seconds, pairs)
            if (text.isBlank()) {
                panel?.setRelationResult("模型没有返回所选人物的关系，请重试")
                return@launch
            }
            panel?.setRelationResult(text)
            val card = state.cards[main] ?: Card(main, "", 0, System.currentTimeMillis(), 0)
            state.cards[main] = card.copy(
                relations = mergeRelations(card.relations, text, seconds),
                relationsAt = System.currentTimeMillis(),
            )
            state.save(this@OverlayService)
            syncReverseRelations(local, main, seconds, text)
            android.widget.Toast.makeText(this@OverlayService, "关系列表已存入「" + main + "」", android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                panel?.setRelationBusy(false)
            }
        }
    }

    /** 合并新旧关系行：只替换本次涉及的人物，保留其他人物的旧关系。 */
    private fun mergeRelations(existing: String, fresh: String, names: List<String>): String {
        val keep = existing.lines().filter { line ->
            val left = line.substringBefore("——").trim()
            left.isNotBlank() && names.none { n ->
                left == n || left.startsWith(n + "·") || left.startsWith(n + "（") || left.startsWith(n + "(")
            }
        }
        return (keep + fresh.lines()).filter { it.isNotBlank() }.joinToString("\n")
    }

    /** 生成后自动为每位次人物写入反向关系，只落存档，不在面板展示。 */
    private fun syncReverseRelations(local: LocalBook, main: String, seconds: List<String>, forward: String) {
        reverseJob?.cancel()
        reverseJob = scope.launch {
            val prompt = "主人物：" + main + "\n已知关系：\n" + forward + "\n\n" +
                "请从每位次人物的角度写出反向关系，主语用次人物，格式：\n" +
                "次人物 —— B 是 A 的……（不超过 30 字）\n" +
                "只能使用这些次人物：" + seconds.joinToString("、") + "。不要解释，不要开场白。"
            val out = runCatching {
                Llm(store, UsageStore(this@OverlayService)).complete(prompt, "双向关系", 500).trim()
            }.getOrNull().orEmpty()
            if (out.isBlank()) return@launch
            // 用户已经切换主人物时，丢弃这次过期的反向结果
            val currentMain = panel?.relationMain().orEmpty()
            if (currentMain.isNotBlank() && currentMain != main) return@launch
            val state = BookState.load(this@OverlayService, "local://" + local.fileName, local.name)
            var changed = false
            out.lines().forEach { line ->
                val parts = line.split("——", limit = 2)
                if (parts.size != 2) return@forEach
                val who = parts[0].trim()
                val reverse = parts[1].trim()
                val sec = seconds.firstOrNull { who == it || who.startsWith(it) || it.startsWith(who) } ?: return@forEach
                if (reverse.isBlank()) return@forEach
                // 正向就是「未载」时不要生成反向，否则会写出「资料未载直接关系者」这类病句
                val forwardLine = forward.lines().firstOrNull { it.contains(sec) }.orEmpty()
                if (forwardLine.contains("未载") || forwardLine.contains("未见") || forwardLine.contains("资料片段不详")) return@forEach
                if (reverse.contains("未载") || reverse.contains("未见") || reverse.contains("资料片段不详") || reverse.contains("B 是 A")) return@forEach
                val c = state.cards[sec] ?: Card(sec, "", 0, System.currentTimeMillis(), 0)
                val lineText = main + " —— " + reverse
                state.cards[sec] = c.copy(
                    relations = mergeRelations(c.relations, lineText, listOf(main)),
                    relationsAt = System.currentTimeMillis(),
                )
                changed = true
            }
            if (changed) state.save(this@OverlayService)
        }
    }

    /** 只保留用户勾选的次人物，缺的补一行「资料未载直接关系」。 */
    private fun filterRelations(raw: String, seconds: List<String>, pairs: Map<String, List<String>> = emptyMap()): String {
        fun norm(s: String) = s.replace(" ", "").replace("\u3000", "")
        val wanted = seconds.map { norm(it) }
        val picked = LinkedHashMap<String, String>()
        raw.lines().forEach { line ->
            val text = line.trim().removePrefix("-").removePrefix("·").trim()
            if (text.isBlank()) return@forEach
            val sep = listOf("——", "—", " - ", "：", ":").map { text.indexOf(it) }.filter { it > 0 }.minOrNull() ?: return@forEach
            var left = text.substring(0, sep).trim().trimStart('1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '.', '、')
            val right = text.substring(sep).trim().trimStart('—', '-', '：', ':').trim()
            val leftNorm = norm(left)
            val match = wanted.firstOrNull { w -> leftNorm == w || leftNorm.startsWith(w) || w.startsWith(leftNorm) }
            if (match != null && match !in picked) picked[match] = right
        }
        if (picked.isEmpty()) return raw.trim()
        return seconds.joinToString("\n") { name ->
            val key = norm(name)
            val value = picked[key]
            val fallback = pairs[name]?.firstOrNull()?.take(80)?.let { "原文共现：" + it }
            val line = when {
                value == null -> fallback ?: "资料片段不详"
                value.contains("资料片段不详") && fallback != null -> fallback
                else -> value
            }
            name + " —— " + line
        }
    }

    private fun rebuildNameIndex() {
        val local = session.boundLocalBook()
        if (local == null) {
            panel?.showCalibrationResult("先在页码校准里选择本书")
            return
        }
        job?.cancel()
        job = scope.launch {
            panel?.showCalibrationResult("建立人名索引…")
            val started = System.currentTimeMillis()
            val index = withContext(Dispatchers.IO) {
                NameIndexBuilder.full(
                    local,
                    onProgress = { done, total ->
                        if (done == total || done % 10 == 0) {
                            main.post { panel?.showCalibrationResult("建立人名索引 " + done + "/" + total + "…") }
                        }
                    },
                ) { chapter -> localStore.chapterText(local, chapter) }
            }
            withContext(Dispatchers.IO) { NameIndexStore.save(this@OverlayService, local, index.entities) }
            NameIndexBuilder.invalidate()
            val seconds = (System.currentTimeMillis() - started) / 1000
            panel?.showCalibrationResult("索引完成：" + index.entities.size + " 个实体，用时 " + seconds + " 秒")
        }
    }

    private fun ocrCalibrate() {
        ocrController.readyError()?.let { error ->
            panel?.showCalibrationResult(error)
            return
        }
        job?.cancel()
        job = scope.launch {
            panel?.showCalibrationResult("截屏识别中…")
            val text = captureScreenText()
            if (text.isBlank()) {
                panel?.showCalibrationResult(ocrController.lastError ?: "没取到屏幕文字")
                return@launch
            }
            val pageNo = ocrController.parsePageNumber(text)
            if (pageNo == null) {
                panel?.showCalibrationResult("没在屏幕文字里读到页码（如 12/40、第 12 页）")
                return@launch
            }
            var chapter = 0
            var pos = 0
            if (store.source == "readest") {
                val local = session.boundLocalBook()
                if (local == null) {
                    panel?.showCalibrationResult("先在「页码校准」里手动指定章节位置")
                    return@launch
                }
                val saved = store.readestPos(local.name)
                chapter = saved?.first ?: 0
                pos = saved?.second ?: 0
                if (pos <= 0) {
                    val h = ocrController.locate(local, text, chapter, allowAi = true).hit
                    if (h != null) {
                        chapter = h.chapter
                        pos = h.pos
                    }
                }
            } else {
                val b = withContext(Dispatchers.IO) { runCatching { LegadoApi(store.legadoPort).currentBook() }.getOrNull() }
                if (b == null) {
                    panel?.showCalibrationResult("拿不到 legado 当前位置")
                    return@launch
                }
                chapter = b.durChapterIndex
                pos = b.durChapterPos
            }
            val cpp = PageEstimator.calibrate(store, session.currentBookKey(), session.sourceKey(), chapter, pos, pageNo)
            if (cpp == null) {
                panel?.showCalibrationResult("页码不合理（第 " + pageNo + " 页，章内偏移 " + pos + "）")
            } else {
                panel?.showCalibrationResult("已校准：" + cpp.toInt() + " 字/页（第 " + (chapter + 1) + " 项，第 " + pageNo + " 页）")
            }
        }
    }

    private fun showChapterPicker() {
        val local = session.boundLocalBook()
        if (local == null) {
            render(UiState.Info("先在运行页导入同一本 EPUB"))
            return
        }
        val items = local.chapters.mapIndexed { i, c ->
            (i + 1).toString() + ". " + c.title.trim().replace(Regex("[\\s　]+"), " ")
        }.toTypedArray()
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("选择章节（共 " + local.chapters.size + " 项）")
            .setItems(items) { _, which ->
                store.manualBook = local.name
                store.manualChapter = which
                store.manualPos = 0
                store.setReadestPos(local.name, which, 0)
                val title = session.chapterTitleOf(local, which)
                panel?.setBookLabel("《" + local.name + "》 " + title + " · 手动位置")
                panel?.setChapterLabel("当前章节：第 " + (which + 1) + " 项 " + title)
                render(UiState.Info("已设为：" + title))
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun askChapter(book: LocalBook) {
        val input = android.widget.EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            hint = "1 – " + book.chapters.size
            setText((store.manualChapter + 1).toString())
        }
        val dialog = android.app.AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(book.name)
            .setMessage("填入你当前在读的章节序号（1 起）")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                val n = input.text.toString().trim().toIntOrNull()
                if (n == null || n < 1 || n > book.chapters.size) {
                    render(UiState.Error("章节序号应在 1 – " + book.chapters.size + " 之间"))
                    return@setPositiveButton
                }
                store.manualBook = book.name
                store.manualChapter = n - 1
                store.manualPos = 0
                store.lastBook = book.name
                store.lastChapter = n - 1
                val title = session.chapterTitleOf(book, n - 1)
                panel?.setBookLabel("《" + book.name + "》 " + title + " · 手动位置")
                render(UiState.Info("已设为：" + title + "（第 " + n + " 项），接着查人名即可"))
            }
            .setNegativeButton("取消", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun identifyPage() {
        job?.cancel()
        job = scope.launch {
            panel?.markIdentifying()
            val api = LegadoApi(store.legadoPort)
            var book = withContext(Dispatchers.IO) { runCatching { api.currentBook() }.getOrNull() }
            var manualLocal: LocalBook? = null
            if (book == null) {
                book = withContext(Dispatchers.IO) { session.manualBookInfo() }
                if (book != null) manualLocal = withContext(Dispatchers.IO) { localStore.findMatching(book!!.name) }
            }
            if (book == null) {
                main.post { panel?.showIdentifyError("拿不到当前书；可先设手动位置") }
                return@launch
            }
            val text = withContext(Dispatchers.IO) {
                if (manualLocal != null) {
                    runCatching { localStore.chapterText(manualLocal!!, book!!.durChapterIndex) }.getOrNull()
                } else {
                    runCatching { api.content(book!!.bookUrl, book!!.durChapterIndex) }.getOrNull()
                }
            }
            if (text.isNullOrBlank()) {
                main.post { panel?.showIdentifyError("没能取到当前章正文") }
                return@launch
            }
            val cpp = session.effectiveCpp()
            val from = (book.durChapterPos - cpp / 2).coerceAtLeast(0)
            val to = (book.durChapterPos + cpp + cpp / 2).coerceAtMost(text.length)
            val pageText = text.substring(from, to)
            val prompt = "下面是小说的当前页正文。只提取这一页里出现的人物称呼，每行一个，最多 10 个。" +
                "不要解释、不要编号、不要标点，不要输出不是人名的词。\n\n" + pageText
            val out = runCatching {
                Llm(store, UsageStore(this@OverlayService)).complete(prompt, "快速识别", 300)
            }.getOrElse {
                main.post { panel?.showIdentifyError("识别失败：" + (it.message ?: "未知")) }
                return@launch
            }
            val names = out.lines()
                .map { it.trim().trim('·', '-', '—', '*', '、', '。', '，', ' ', '\t') }
                .filter { it.length in 2..8 && it.none { c -> c.isDigit() } }
                .distinct()
                .take(10)
            main.post { panel?.showNames(names) }
        }
    }

    private fun calibratePage(page: Int) {
        job?.cancel()
        job = scope.launch {
            val api = LegadoApi(store.legadoPort)
            if (store.source == "readest") {
                val local = session.boundLocalBook()
                val pos = local?.let { store.readestPos(it.name) }
                val cpp = if (local != null && pos != null) {
                    PageEstimator.calibrate(store, local.name, "readest", pos.first, pos.second, page)
                } else null
                main.post {
                    panel?.showCalibrationResult(
                        if (cpp == null) "需要先有章内位置（点一次 OCR 识别）"
                        else "已校准：" + cpp.toInt() + " 字/页（第 " + ((pos?.first ?: 0) + 1) + " 项）"
                    )
                }
                return@launch
            }
            val b = withContext(Dispatchers.IO) { runCatching { api.currentBook() }.getOrNull() }
            if (b == null) {
                main.post { panel?.showCalibrationResult("拿不到当前书") }
                return@launch
            }
            val cpp = PageEstimator.calibrate(store, b.name, "legado", b.durChapterIndex, b.durChapterPos, page)
            if (cpp == null) {
                main.post { panel?.showCalibrationResult("页码不合理，检查一下") }
            } else {
                store.lastBook = b.name
                store.lastChapter = b.durChapterIndex
                main.post { panel?.showCalibrationResult("已校准：" + cpp.toInt() + " 字/页（第 " + (b.durChapterIndex + 1) + " 项）") }
            }
        }
    }

    private fun mergeAlias() {
        val book = lastBook ?: return
        if (lastName.isBlank()) return
        val set = store.aliasMerged
        set.add(book.bookUrl + "|" + lastName)
        store.aliasMerged = set
        onSubmit(lastName, false)
    }

    private fun setLatest() {
        val book = lastBook ?: return
        val state = BookState.load(this, book.bookUrl, book.name)
        val pos = Position.global(book.durChapterIndex, book.durChapterPos)
        state.resetAt(pos)
        state.save(this)
        render(UiState.Info("已把当前页设为最新，档案与回读缓存已清空"))
    }

    private fun onJump(hit: TraceHit) {
        if (store.source == "readest") {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("Eread", hit.excerpt))
            render(UiState.Info("已复制该段原文"))
            return
        }
        job?.cancel()
        job = scope.launch {
            val api = LegadoApi(store.legadoPort)
            val book = withContext(Dispatchers.IO) { runCatching { api.currentBook() }.getOrNull() } ?: return@launch
            var legadoIndex = hit.chapterIndex
            val local = withContext(Dispatchers.IO) { runCatching { localStore.findMatching(book.name) }.getOrNull() }
            if (local != null) {
                val chapters = withContext(Dispatchers.IO) { runCatching { api.chapters(book.bookUrl) }.getOrDefault(emptyList()) }
                legadoIndex = ChapterMapper.legadoIndex(local, chapters, hit.chapterIndex)
            }
            collapse()
            val pkg = legadoPackage() ?: return@launch
            val intent = Intent().apply {
                setClassName(pkg, "io.legado.app.ui.book.read.ReadBookActivity")
                putExtra("bookUrl", book.bookUrl)
                putExtra("index", legadoIndex)
                putExtra("chapterPos", hit.position)
                putExtra("inBookshelf", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { startActivity(intent) }.onFailure { Log.e("Eread", "jump failed", it) }
        }
    }

    private fun norm(s: String): String =
        s.replace(Regex("[\\s\\p{Punct}·：:（）()《》\\[\\]]"), "").lowercase()

    private fun legadoPackage(): String? = CANDIDATES.firstOrNull {
        runCatching { packageManager.getPackageInfo(it, 0) }.isSuccess
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName))
        job?.cancel()
        panel?.let { runCatching { wm.removeView(it) } }
        if (::ballWindow.isInitialized) ballWindow.hide()
        if (store.captureMode == "projection") {
            runCatching { stopService(Intent(this, ProjectionService::class.java)) }
        }
        panel = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STATE = "com.eread.companion.OVERLAY_STATE"

        private val CANDIDATES = listOf(
            "com.legado.app.release",
            "com.legado.app",
            "com.legado.app.debug",
            "io.legado.app",
        )
    }
}
