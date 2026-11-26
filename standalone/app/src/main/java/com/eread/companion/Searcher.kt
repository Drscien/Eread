package com.eread.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

class Searcher(private val api: LegadoApi) {

    private val frontMatter = Regex(
        "^(序|序言|前言|引言|目录|内容简介|简介|作品相关|作者的话|上架感言|感言|公告|通知|请假|说明|" +
            "完本感言|新书感言|推荐票|月票|致读者|更新说明|索引|注释|译后记|后记|附录|版权|书名页|扉页|" +
            "cover|titlepage|title page|toc|contents|index|copyright|dedication|foreword|preface|notes|" +
            "bibliography|appendix).{0,12}$",
        RegexOption.IGNORE_CASE
    )

    suspend fun scan(
        book: BookInfo,
        query: String,
        onProgress: (String) -> Unit = {},
    ): ScanResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val chapters = api.chapters(book.bookUrl)
        if (chapters.isEmpty()) error("拿不到章节目录，检查 legado 的 Web 服务是否开启")

        val titleMap = chapters.associate { it.index to it.title }
        val hits = ArrayList<TraceHit>()
        var scanned = 0
        var fetched = 0
        var earliestIndex = -1
        var earliestTitle = ""
        var truncated = false
        var consecutiveMiss = 0

        var cursor = book.durChapterIndex.coerceAtMost(chapters.last().index)
        while (cursor >= 0) {
            currentCoroutineContext().ensureActive()
            if (scanned >= MAX_CHAPTERS || System.currentTimeMillis() - started > BUDGET_MS) {
                truncated = true
                break
            }
            val chapterIndex = cursor
            cursor--
            scanned++

            val title = titleMap[chapterIndex] ?: ""
            if (title.isNotBlank() && frontMatter.matches(title.trim().replace(Regex("[\\s　]+"), ""))) continue

            val raw = runCatching { api.content(book.bookUrl, chapterIndex) }.getOrNull()
            if (raw.isNullOrBlank()) {
                consecutiveMiss++
                if (consecutiveMiss >= 6) {
                    truncated = true
                    break
                }
                continue
            }
            consecutiveMiss = 0
            fetched++

            val text = if (chapterIndex == book.durChapterIndex && book.durChapterPos > 0) {
                raw.take(book.durChapterPos.coerceAtMost(raw.length))
            } else {
                raw
            }

            val positions = findPositions(text, query, 6)
            if (positions.isNotEmpty()) {
                earliestIndex = chapterIndex
                earliestTitle = title
                positions.forEach { p -> hits.add(TraceHit(chapterIndex, title, p, excerpt(text, p, query))) }
            }
            if (scanned % 20 == 0) onProgress("已回扫 " + scanned + " 章，命中 " + hits.size + " 处")
        }

        if (hits.isEmpty()) {
            return@withContext ScanResult(query, -1, "", 0, emptyList(), scanned, fetched, truncated, anchorChapter = book.durChapterIndex)
        }

        val ordered = hits.sortedBy { it.chapterIndex }
        ScanResult(
            query = query,
            firstChapterIndex = earliestIndex,
            firstChapterTitle = earliestTitle,
            hitCount = ordered.size,
            sampled = sample(ordered, MAX_HITS),
            scanned = scanned,
            fetched = fetched,
            truncated = truncated,
            anchorChapter = book.durChapterIndex,
        )
    }

    private fun findPositions(text: String, query: String, limit: Int): List<Int> {
        val out = ArrayList<Int>(limit)
        var from = 0
        while (out.size < limit) {
            val i = text.indexOf(query, from)
            if (i < 0) break
            out.add(i)
            from = i + query.length
        }
        return out
    }

    private fun excerpt(text: String, position: Int, query: String): String {
        var start = max(0, position - 70)
        // 不要从人名中间截断
        val prev = text.lastIndexOf(query, position - 1)
        if (prev in 0 until start && prev + query.length > start) start = prev
        if (start > 0) {
            val seg = text.substring(start, position)
            val p = seg.lastIndexOfAny(charArrayOf('。', '！', '？', '；', '\n'))
            if (p >= 0) start += p + 1
        }
        while (start < position && text[start].isWhitespace()) start++
        val end = min(text.length, position + query.length + 90)
        return text.substring(start, end).replace(Regex("[\\r\\n\\t ]+"), " ").trim()
    }

    private fun sample(hits: List<TraceHit>, max: Int): List<TraceHit> {
        if (hits.size <= max) return hits
        val out = LinkedHashMap<Int, TraceHit>()
        val step = (hits.size - 1).toDouble() / (max - 1)
        for (i in 0 until max) {
            val idx = (i * step).toInt().coerceIn(0, hits.lastIndex)
            out[idx] = hits[idx]
        }
        return out.values.toList().sortedBy { it.chapterIndex }
    }

    companion object {
        private const val MAX_CHAPTERS = 120
        private const val MAX_HITS = 48
        private const val BUDGET_MS = 15000L
    }
}
