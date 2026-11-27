package io.legado.app.ui.book.read.trace

import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 从当前阅读位置向第 1 章回扫，找出某个人名的最早出场与代表性片段。
 *
 * 只读本地已缓存章节（BookHelp.getContent 返回 null 表示未下载），
 * 不触发网络请求，因此不会卡住阅读、也不会因书源失效而失败。
 */
class TraceSearcher(private val book: Book) {

    suspend fun scan(
        query: String,
        upToIndex: Int,
        upToOffset: Int,
        maxHits: Int = 48,
        maxChapters: Int = 600,
        budgetMs: Long = 8000L,
        onProgress: (String) -> Unit = {},
    ): TraceScanResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val chapters = appDb.bookChapterDao.getChapterList(book.bookUrl)
            .asSequence()
            .filter { !it.isVolume }
            .sortedBy { it.index }
            .toList()

        val hits = ArrayList<TraceHit>()
        var scanned = 0
        var cached = 0
        var earliestIndex = -1
        var earliestTitle = ""
        var truncated = false

        var cursor = chapters.indexOfLast { it.index <= upToIndex }
        while (cursor >= 0) {
            currentCoroutineContext().ensureActive()
            val elapsed = System.currentTimeMillis() - startedAt
            if (scanned >= maxChapters || elapsed > budgetMs) {
                truncated = true
                break
            }
            val chapter = chapters[cursor]
            cursor--
            scanned++

            val content = runCatching { BookHelp.getContent(book, chapter) }.getOrNull()
            if (content.isNullOrBlank()) continue
            cached++

            val text = if (chapter.index == upToIndex && upToOffset > 0) {
                content.take(upToOffset.coerceAtMost(content.length))
            } else {
                content
            }
            if (TraceFrontMatter.isFrontMatter(chapter, text.length)) continue

            val positions = findPositions(text, query, limit = 6)
            if (positions.isNotEmpty()) {
                earliestIndex = chapter.index
                earliestTitle = chapter.title
                for (p in positions) {
                    hits.add(
                        TraceHit(
                            chapterIndex = chapter.index,
                            chapterTitle = chapter.title,
                            position = p,
                            excerpt = excerpt(text, p, query.length),
                        )
                    )
                }
            }
            if (scanned % 25 == 0) {
                onProgress("已回扫 " + scanned + " 章，命中 " + hits.size + " 处")
            }
        }

        if (hits.isEmpty()) {
            return@withContext TraceScanResult(
                query = query,
                firstChapterIndex = -1,
                firstChapterTitle = "",
                hitCount = 0,
                sampledHits = emptyList(),
                scannedChapters = scanned,
                cachedChapters = cached,
                truncated = truncated,
                elapsedMs = System.currentTimeMillis() - startedAt,
            )
        }

        val ordered = hits.sortedWith(compareBy({ it.chapterIndex }, { it.position }))
        val sampled = sampleEvenly(ordered, maxHits)

        TraceScanResult(
            query = query,
            firstChapterIndex = earliestIndex,
            firstChapterTitle = earliestTitle,
            hitCount = ordered.size,
            sampledHits = sampled,
            scannedChapters = scanned,
            cachedChapters = cached,
            truncated = truncated,
            elapsedMs = System.currentTimeMillis() - startedAt,
        )
    }

    private fun findPositions(text: String, query: String, limit: Int): List<Int> {
        val out = ArrayList<Int>(limit)
        var from = 0
        while (out.size < limit) {
            val idx = text.indexOf(query, from)
            if (idx < 0) break
            out.add(idx)
            from = idx + query.length
        }
        return out
    }

    private fun excerpt(text: String, position: Int, length: Int): String {
        val start = max(0, position - 60)
        val end = min(text.length, position + length + 80)
        return text.substring(start, end)
            .replace(Regex("[\\r\\n\\t ]+"), " ")
            .trim()
    }

    /** 首次 + 等距抽样 + 最后一次，避免长书里几千处命中把上下文撑爆 */
    private fun sampleEvenly(hits: List<TraceHit>, maxHits: Int): List<TraceHit> {
        if (hits.size <= maxHits) return hits
        val out = LinkedHashMap<Int, TraceHit>()
        val step = (hits.size - 1).toDouble() / (maxHits - 1)
        for (i in 0 until maxHits) {
            val idx = (i * step).toInt().coerceIn(0, hits.lastIndex)
            val hit = hits[idx]
            out[hit.chapterIndex * 100000 + hit.position] = hit
        }
        return out.values.toList()
    }
}
