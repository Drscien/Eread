package com.eread.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次人物查询：取书 → 判定缓存/回读 → 扫描 → 生成摘要 → 写档案。 */
class QueryController(
    private val context: Context,
    private val store: Store,
    private val localStore: LocalStore,
    private val session: ReadingSession,
    private val onState: (UiState) -> Unit,
    private val onBook: (BookInfo, String) -> Unit,
) {

    suspend fun run(name: String, force: Boolean) {
        val readest = store.source == "readest"
        onState(UiState.Busy(if (readest) "读取本地位置…" else "连接 legado…"))
        val api = LegadoApi(store.legadoPort)
        val bookOutcome = if (readest) null else withContext(Dispatchers.IO) { runCatching { api.currentBook() } }
        var book = bookOutcome?.getOrNull()
        if (book == null) {
            book = withContext(Dispatchers.IO) { session.manualBookInfo() }
            if (book == null) {
                val reason = bookOutcome?.exceptionOrNull()?.let { it.javaClass.simpleName + ": " + it.message } ?: "未取到当前书"
                val hint = if (!readest && bookOutcome?.isFailure == true) {
                    "legado 的 Web 服务可能没开；改用 Readest 模式更快"
                } else {
                    "可在页码校准里选择本地书与章节"
                }
                onState(UiState.Error("拿不到书籍：" + reason + "；" + hint))
                return
            }
        }
        store.lastBook = book.name
        store.lastChapter = book.durChapterIndex
        onBook(book, name)
        store.addRecentSearch(book.name, name)
        val anchorGlobal = Position.global(book.durChapterIndex, book.durChapterPos)
        val state = BookState.load(context, book.bookUrl, book.name)
        state.currentPos = anchorGlobal
        state.save(context)
        val local = withContext(Dispatchers.IO) { runCatching { localStore.findMatching(book.name) }.getOrNull() }
        var target = name
        var targetLatin = ""
        if (local != null) {
            val seeds = (state.cards.keys + store.ocrNames(book.name) + name).toList()
            val preload = withContext(Dispatchers.IO) { NameIndexStore.load(context, local) }
            val index = withContext(Dispatchers.IO) {
                NameIndexBuilder.get(local, seeds, preload) { chapter -> localStore.chapterText(local, chapter) }
            }
            withContext(Dispatchers.IO) { NameIndexStore.save(context, local, index.entities) }
            when (val resolution = index.resolve(name)) {
                is NameResolution.Ambiguous -> {
                    // 只清理空档案；已有关系/概述的档案不能因歧义被删除
                    val existing = state.cards[name]
                    if (existing != null && existing.summary.isBlank() && existing.biography.isBlank() && existing.relations.isBlank()) {
                        state.cards.remove(name)
                        state.save(context)
                    }
                    onState(UiState.Choose(name, resolution.candidates))
                    return
                }
                is NameResolution.Unique -> {
                    target = resolution.entity.full
                    targetLatin = resolution.entity.latin
                    if (target != name && state.cards[name] != null) {
                        state.cards[target] = state.cards.remove(name)!!
                        state.save(context)
                    }
                    val existing = state.cards[target]
                    if (existing != null && existing.latin.isBlank() && targetLatin.isNotBlank()) {
                        state.cards[target] = existing.copy(latin = targetLatin)
                        state.save(context)
                    }
                }
                else -> {
                    // 没有索引证据时，不把可能是简称的名字写成档案
                    val bareLatin = name.matches(Regex("[A-Za-z][A-Za-z.'\\-]*"))
                    val chineseVariants = state.cards.keys.filter { it != name && it.startsWith(name) && name.length <= 3 }
                    if (bareLatin && name.length <= 12) {
                        if (state.cards.remove(name) != null) state.save(context)
                        onState(UiState.Error("「" + name + "」可能是简称，请用全名（如 " + name + " IV）"))
                        return
                    }
                    if (name.any { it.code in 0x4e00..0x9fff } && chineseVariants.size >= 2) {
                        val candidates = chineseVariants.mapNotNull { full ->
                            NameParser.parse(full)?.let { p ->
                                NameEntity(p.full, p.base, p.regnal, p.segments)
                            }
                        }
                        onState(UiState.Choose(name, candidates.take(8)))
                        return
                    }
                }
            }
        }
        val card = state.cards[target]
        val isReread = !force && card != null && anchorGlobal < state.latestPos
        val isCached = !force && card != null && !isReread && anchorGlobal <= card.pos
        val rereadBucket = anchorGlobal / store.rereadBucket
        val cachedReread = if (isReread) {
            state.rereads.firstOrNull { it.name == target && it.bucket == rereadBucket }
        } else null

        if (isCached) {
            val hits = withContext(Dispatchers.IO) { scan(api, book, local, target) }
            if (hits != null) {
                onState(UiState.Done(hits, card!!.summary, true, "档案无新内容", "最新 · 缓存", canRegenerate = true))
                return
            }
        }
        if (cachedReread != null) {
            val hits = withContext(Dispatchers.IO) { scan(api, book, local, target) }
            if (hits != null) {
                onState(UiState.Done(hits, cachedReread.summary, true, "回读缓存", "回读 · 缓存", canRegenerate = true))
                return
            }
        }

        onState(UiState.Busy("在《" + book.name + "》中检索…"))
        val result = withContext(Dispatchers.IO) { runCatching { doScan(api, book, local, target) } }
            .getOrElse {
                onState(UiState.Error("检索失败：" + (it.localizedMessage ?: "未知错误")))
                return
            }
        if (result.hitCount == 0) {
            if (result.suggestion != null) {
                onState(UiState.Error("没找到「" + result.query + "」，可能是错字或少字。", result.suggestion))
            } else {
                onState(UiState.Error("回扫 " + result.scanned + " 章没找到「" + result.query + "」。"))
            }
            return
        }

        val mode = SummaryPlanner.mode(
            force = force,
            hasCard = card != null,
            isReread = isReread,
            anchorPos = anchorGlobal,
            cardPos = card?.pos ?: 0,
        )
        if (store.apiKey.isBlank()) {
            onState(UiState.Done(result, null, false, "未配置 API Key", if (isReread) "回读" else "最新"))
            return
        }

        val busyText = when (mode) {
            SummaryMode.FULL -> if (force) "重新生成档案…" else "首次生成档案…"
            SummaryMode.INCREMENTAL -> "增量更新档案…"
            SummaryMode.REREAD -> "生成回读概述…"
        }
        onState(UiState.Busy(busyText + "（" + result.hitCount + " 处命中）"))
        val outcome = withContext(Dispatchers.IO) {
            runCatching {
                val feature = when (mode) {
                    SummaryMode.FULL -> if (card == null) "首次概述" else "重新生成概述"
                    SummaryMode.INCREMENTAL -> "增量概述"
                    SummaryMode.REREAD -> "回读概述"
                }
                Llm(store, UsageStore(context)).summarize(result, mode, card?.summary, feature)
            }
        }
        val summary = outcome.getOrNull()
        if (summary == null) {
            val err = outcome.exceptionOrNull()?.localizedMessage ?: "未知"
            onState(UiState.Done(result, null, false, "总结失败：" + err, if (isReread) "回读" else "最新"))
            return
        }

        if (mode == SummaryMode.REREAD) {
            state.rereads.removeAll { it.name == target && it.bucket == rereadBucket }
            state.rereads.add(RereadEntry(target, rereadBucket, summary, System.currentTimeMillis()))
            state.save(context)
            onState(UiState.Done(result, summary, false, "回读 · 未改动档案", "回读", canRegenerate = true))
        } else {
            state.cards[target] = Card(
                target,
                summary,
                anchorGlobal,
                System.currentTimeMillis(),
                (card?.updates ?: 0) + 1,
                latin = card?.latin?.takeIf { it.isNotBlank() } ?: targetLatin,
            )
            if (anchorGlobal > state.latestPos) state.latestPos = anchorGlobal
            state.save(context)
            onState(UiState.Done(result, summary, false, null, "最新", canRegenerate = true))
        }
    }

    private suspend fun scan(api: LegadoApi, book: BookInfo, local: LocalBook?, name: String): ScanResult? {
        return runCatching { doScan(api, book, local, name) }.getOrNull()
    }

    private suspend fun doScan(api: LegadoApi, book: BookInfo, local: LocalBook?, name: String): ScanResult {
        return if (local != null) {
            val manual = book.bookUrl.startsWith("local://")
            val from = if (manual) {
                book.durChapterIndex.coerceIn(0, local.chapters.lastIndex)
            } else {
                val chapters = runCatching { api.chapters(book.bookUrl) }.getOrDefault(emptyList())
                ChapterMapper.localIndex(local, chapters, book.durChapterIndex)
            }
            // 与 Readest 搜索口径一致：当前章整章计入，后续章节不计
            LocalSearcher(localStore).scan(
                local,
                name,
                from,
                if (manual) 0 else book.durChapterPos,
                sampleMax = store.hitSample,
                perChapterLimit = store.perChapterHits,
                aliasThreshold = store.aliasThreshold,
            )
        } else {
            Searcher(api).scan(book, name)
        }
    }
}
