package com.eread.companion

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class OverlayService : Service() {


    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private lateinit var localStore: LocalStore
    private var ball: View? = null
    private lateinit var ballLp: WindowManager.LayoutParams
    private val radialViews = ArrayList<View>()
    private val radialOffsets = HashMap<View, Pair<Float, Float>>()
    private var radialScrim: View? = null
    private var radialOpen = false
    private val radialAutoHide = Runnable { hideRadial() }
    private var panel: PanelView? = null
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
        startAsForeground()
        store = Store(this)
        localStore = LocalStore(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        showBall()
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

    private fun showBall() {
        if (ball != null) return
        val size = dp(36)
        val metrics = resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val v = BallView(this)
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (store.ballX >= 0f) (store.ballX * screenW).toInt().coerceIn(0, screenW - size)
            else screenW - size - dp(8)
            y = if (store.ballY >= 0f) (store.ballY * screenH).toInt().coerceIn(0, screenH - size)
            else screenH / 2 - size / 2
        }
        ballLp = lp
        attachBallTouch(v, lp)
        wm.addView(v, lp)
        ball = v
    }

    private fun attachBallTouch(v: View, lp: WindowManager.LayoutParams) {
        val slop = dp(6)
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longFired = false
        val longPress = Runnable {
            longFired = true
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            showRadial()
        }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longFired = false
                    main.postDelayed(longPress, 500)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        main.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    main.removeCallbacks(longPress)
                    if (!dragging && !longFired) {
                        if (radialOpen) hideRadial() else expand()
                    }
                    if (dragging) {
                        val m = resources.displayMetrics
                        store.ballX = lp.x.toFloat() / m.widthPixels
                        store.ballY = lp.y.toFloat() / m.heightPixels
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    true
                }
                else -> false
            }
        }
    }

    private fun hideBall() {
        hideRadial()
        ball?.let { runCatching { wm.removeView(it) } }
        ball = null
    }

    private fun expand() {
        if (panel != null) return
        Theme.apply(this)
        hideBall()
        val p = PanelView(
            this,
            onSubmit = { name -> onSubmit(name, false) },
            onClose = { collapse() },
            onJump = { hit -> onJump(hit) },
            pageOfHit = { chapter, pos -> PageEstimator.estimate(store, chapter, pos) },
            onRegenerate = { if (lastName.isNotBlank()) onSubmit(lastName, true) },
            onSetLatest = { setLatest() },
            onAliasMerge = { mergeAlias() },
            onCalibrate = { page -> calibratePage(page) },
            onIdentify = { identifyPage() },
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

    private fun showRadial() {
        if (radialOpen) {
            hideRadial()
            return
        }
        radialOpen = true
        val m = resources.displayMetrics
        val s = ballLp.width
        val cx = ballLp.x + s / 2f
        val cy = ballLp.y + s / 2f
        val radius = dp(67)
        val base = if (cx < m.widthPixels / 2f) 0.0 else 180.0
        val bubble = dp(42)

        val scrim = View(this).apply {
            alpha = 0f
            setOnClickListener { hideRadial() }
        }
        val scrimLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        runCatching { wm.addView(scrim, scrimLp) }
        radialScrim = scrim

        val items = listOf(
            Triple("设置", 0, R.drawable.ic_settings),
            Triple("停止", 1, R.drawable.ic_close_red),
        )
        items.forEach { (_, index, iconRes) ->
            val angle = Math.toRadians(base + if (index == 0) -45.0 else 45.0)
            val bx = (cx + radius * Math.cos(angle)).toInt() - bubble / 2
            val by = (cy + radius * Math.sin(angle)).toInt() - bubble / 2
            val holder = FrameLayout(this).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0x661F2430)
                }
                alpha = 0f
            }
            holder.addView(ImageView(this).apply {
                setImageResource(iconRes)
                if (index == 0) setColorFilter(0xE6E8EAED.toInt())
            }, FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
            holder.setOnClickListener {
                hideRadial()
                if (index == 0) {
                    val intent = Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra("tab", 2)
                    runCatching { startActivity(intent) }
                } else {
                    stopSelf()
                }
            }
            val lp = WindowManager.LayoutParams(
                bubble, bubble,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = bx.coerceIn(0, m.widthPixels - bubble)
                y = by.coerceIn(0, m.heightPixels - bubble)
            }
            val dx = cx - (lp.x + bubble / 2f)
            val dy = cy - (lp.y + bubble / 2f)
            holder.translationX = dx
            holder.translationY = dy
            holder.scaleX = 0.55f
            holder.scaleY = 0.55f
            radialOffsets[holder] = dx to dy
            runCatching { wm.addView(holder, lp) }
            radialViews.add(holder)
            holder.animate()
                .translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f)
                .alpha(1f)
                .setDuration(210)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
        main.postDelayed(radialAutoHide, 5000)
    }

    private fun hideRadial() {
        main.removeCallbacks(radialAutoHide)
        if (!radialOpen) return
        radialOpen = false
        val list = radialViews.toList()
        radialViews.clear()
        list.forEach { v ->
            val off = radialOffsets.remove(v) ?: (0f to 0f)
            v.isClickable = false
            v.animate()
                .translationX(off.first).translationY(off.second)
                .scaleX(0.55f).scaleY(0.55f)
                .alpha(0f)
                .setDuration(170)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .start()
        }
        val scrim = radialScrim
        radialScrim = null
        main.postDelayed({
            list.forEach { runCatching { wm.removeView(it) } }
            scrim?.let { runCatching { wm.removeView(it) } }
        }, 190)
    }

    private fun collapse() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        showBall()
    }

    private fun render(state: UiState) {
        if (state is UiState.Done || state is UiState.Error) lastUi = state
        main.post { panel?.render(state) }
    }

    private fun onSubmit(name: String, force: Boolean) {
        job?.cancel()
        job = scope.launch {
            render(UiState.Busy("连接 legado…"))
            val api = LegadoApi(store.legadoPort)
            val bookOutcome = withContext(Dispatchers.IO) { runCatching { api.currentBook() } }
            val book = bookOutcome.getOrNull()
            if (book == null) {
                val reason = bookOutcome.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message } ?: "返回为空"
                render(UiState.Error("拿不到书籍：" + reason))
                return@launch
            }
            store.lastBook = book.name
            store.lastChapter = book.durChapterIndex
            lastBook = book
            lastName = name
            val anchorGlobal = Position.global(book.durChapterIndex, book.durChapterPos)
            val state = BookState.load(this@OverlayService, book.bookUrl, book.name)
            state.currentPos = anchorGlobal
            state.save(this@OverlayService)
            val card = state.cards[name]
            val isReread = !force && card != null && anchorGlobal < state.latestPos
            val isCached = !force && card != null && !isReread && anchorGlobal <= card.pos
            val rereadBucket = anchorGlobal / 2000
            val cachedReread = if (isReread) {
                state.rereads.firstOrNull { it.name == name && it.bucket == rereadBucket }
            } else null

            val local = withContext(Dispatchers.IO) { runCatching { localStore.findMatching(book.name) }.getOrNull() }
            if (isCached) {
                val hits = withContext(Dispatchers.IO) { scan(api, book, local, name) }
                if (hits != null) {
                    render(UiState.Done(hits, card!!.summary, true, "档案无新内容", "最新 · 缓存", canRegenerate = true))
                    return@launch
                }
            }
            if (cachedReread != null) {
                val hits = withContext(Dispatchers.IO) { scan(api, book, local, name) }
                if (hits != null) {
                    render(UiState.Done(hits, cachedReread.summary, true, "回读缓存", "回读 · 缓存", canRegenerate = true))
                    return@launch
                }
            }

            render(UiState.Busy("在《" + book.name + "》中检索…"))
            val result = withContext(Dispatchers.IO) { runCatching { doScan(api, book, local, name) } }
                .getOrElse {
                    render(UiState.Error("检索失败：" + (it.localizedMessage ?: "未知错误")))
                    return@launch
                }
            if (result.hitCount == 0) {
                if (result.suggestion != null) {
                    render(UiState.Error("没找到「" + result.query + "」，可能是错字或少字。", result.suggestion))
                } else {
                    render(UiState.Error("回扫 " + result.scanned + " 章没找到「" + result.query + "」。"))
                }
                return@launch
            }

            val mode = when {
                force || card == null -> SummaryMode.FULL
                isReread -> SummaryMode.REREAD
                anchorGlobal > card.pos -> SummaryMode.INCREMENTAL
                else -> SummaryMode.FULL
            }
            if (store.apiKey.isBlank()) {
                render(UiState.Done(result, null, false, "未配置 API Key", if (isReread) "回读" else "最新"))
                return@launch
            }

            val busyText = when (mode) {
                SummaryMode.FULL -> if (force) "重新生成档案…" else "首次生成档案…"
                SummaryMode.INCREMENTAL -> "增量更新档案…"
                SummaryMode.REREAD -> "生成回读概述…"
            }
            render(UiState.Busy(busyText + "（" + result.hitCount + " 处命中）"))
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val feature = when (mode) {
                        SummaryMode.FULL -> if (card == null) "首次概述" else "重新生成概述"
                        SummaryMode.INCREMENTAL -> "增量概述"
                        SummaryMode.REREAD -> "回读概述"
                    }
                    Llm(store, UsageStore(this@OverlayService)).summarize(result, mode, card?.summary, feature)
                }
            }
            val summary = outcome.getOrNull()
            if (summary == null) {
                val err = outcome.exceptionOrNull()?.localizedMessage ?: "未知"
                render(UiState.Done(result, null, false, "总结失败：" + err, if (isReread) "回读" else "最新"))
                return@launch
            }

            if (mode == SummaryMode.REREAD) {
                state.rereads.removeAll { it.name == name && it.bucket == rereadBucket }
                state.rereads.add(RereadEntry(name, rereadBucket, summary, System.currentTimeMillis()))
                state.save(this@OverlayService)
                render(UiState.Done(result, summary, false, "回读 · 未改动档案", "回读", canRegenerate = true))
            } else {
                state.cards[name] = Card(name, summary, anchorGlobal, System.currentTimeMillis(), (card?.updates ?: 0) + 1)
                if (anchorGlobal > state.latestPos) state.latestPos = anchorGlobal
                state.save(this@OverlayService)
                render(UiState.Done(result, summary, false, null, "最新", canRegenerate = true))
            }
        }
    }

    private suspend fun scan(api: LegadoApi, book: BookInfo, local: LocalBook?, name: String): ScanResult? {
        return runCatching { doScan(api, book, local, name) }.getOrNull()
    }

    private suspend fun doScan(api: LegadoApi, book: BookInfo, local: LocalBook?, name: String): ScanResult {
        return if (local != null) {
            val chapters = runCatching { api.chapters(book.bookUrl) }.getOrDefault(emptyList())
            val from = localIndexOf(local, chapters, book.durChapterIndex)
            LocalSearcher(localStore).scan(local, name, from, book.durChapterPos)
        } else {
            Searcher(api).scan(book, name)
        }
    }

    private fun identifyPage() {
        job?.cancel()
        job = scope.launch {
            panel?.markIdentifying()
            val api = LegadoApi(store.legadoPort)
            val book = withContext(Dispatchers.IO) { runCatching { api.currentBook() }.getOrNull() }
            if (book == null) {
                main.post { panel?.showIdentifyError("拿不到当前书，检查 legado 服务") }
                return@launch
            }
            val text = withContext(Dispatchers.IO) {
                runCatching { api.content(book.bookUrl, book.durChapterIndex) }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                main.post { panel?.showIdentifyError("没能取到当前章正文") }
                return@launch
            }
            val cpp = store.charsPerPage
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
            val b = withContext(Dispatchers.IO) { runCatching { api.currentBook() }.getOrNull() }
            if (b == null) {
                main.post { panel?.showCalibrationResult("拿不到当前书") }
                return@launch
            }
            val cpp = PageEstimator.calibrate(store, b.durChapterIndex, b.durChapterPos, page)
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
        job?.cancel()
        job = scope.launch {
            val api = LegadoApi(store.legadoPort)
            val book = withContext(Dispatchers.IO) { runCatching { api.currentBook() }.getOrNull() } ?: return@launch
            var legadoIndex = hit.chapterIndex
            val local = withContext(Dispatchers.IO) { runCatching { localStore.findMatching(book.name) }.getOrNull() }
            if (local != null) {
                val chapters = withContext(Dispatchers.IO) { runCatching { api.chapters(book.bookUrl) }.getOrDefault(emptyList()) }
                legadoIndex = legadoIndexOf(local, chapters, hit.chapterIndex)
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

    private fun localIndexOf(local: LocalBook, chapters: List<ChapterInfo>, legadoIndex: Int): Int {
        if (chapters.isEmpty()) return legadoIndex.coerceIn(0, local.chapters.lastIndex)
        val byTitle = HashMap<String, Int>()
        local.chapters.forEach { byTitle[norm(it.title)] = it.index }
        chapters.forEach { c ->
            val hit = byTitle[norm(c.title)]
            if (hit != null && c.index == legadoIndex) return hit
        }
        if (chapters.size == local.chapters.size) return legadoIndex.coerceIn(0, local.chapters.lastIndex)
        val scaled = (legadoIndex.toFloat() / chapters.size * local.chapters.size).toInt()
        return scaled.coerceIn(0, local.chapters.lastIndex)
    }

    private fun legadoIndexOf(local: LocalBook, chapters: List<ChapterInfo>, localIndex: Int): Int {
        if (chapters.isEmpty()) return localIndex
        val byTitle = HashMap<String, Int>()
        local.chapters.forEach { byTitle[norm(it.title)] = it.index }
        chapters.forEach { c -> if (byTitle[norm(c.title)] == localIndex) return c.index }
        if (chapters.size == local.chapters.size) return localIndex.coerceIn(0, chapters.lastIndex)
        val scaled = (localIndex.toFloat() / local.chapters.size * chapters.size).toInt()
        return scaled.coerceIn(0, chapters.lastIndex)
    }

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
        ball?.let { runCatching { wm.removeView(it) } }
        panel = null
        ball = null
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
