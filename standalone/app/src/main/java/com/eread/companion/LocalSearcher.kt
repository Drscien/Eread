package com.eread.companion

import kotlin.math.max
import kotlin.math.min

class LocalSearcher(private val store: LocalStore) {

    private val chapterCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 128
    }

    private fun chapterText(book: LocalBook, index: Int): String? {
        val key = book.fileName + "#" + index
        synchronized(chapterCache) { chapterCache[key]?.let { return it } }
        val text = store.chapterText(book, index) ?: return null
        synchronized(chapterCache) { chapterCache[key] = text }
        return text
    }

    private val frontMatter = Regex(
        "^(序|序言|前言|引言|目录|内容简介|简介|作品相关|作者的话|上架感言|感言|公告|通知|请假|说明|" +
            "完本感言|新书感言|推荐票|月票|致读者|更新说明|索引|注释|译后记|后记|附录|版权|书名页|扉页|" +
            "cover|titlepage|toc|contents|index|copyright|dedication|foreword|preface|notes|bibliography|appendix).{0,12}$",
        RegexOption.IGNORE_CASE
    )

    fun scan(
        book: LocalBook,
        query: String,
        fromIndex: Int,
        upToPos: Int,
        mergeAlias: Boolean = false,
        sampleMax: Int = 48,
        perChapterLimit: Int = 500,
        aliasThreshold: Int = 4,
    ): ScanResult {
        val started = System.currentTimeMillis()
        val hits = ArrayList<TraceHit>()
        var scanned = 0
        var fetched = 0
        var earliest = -1
        var earliestTitle = ""
        var truncated = false

        var cursor = fromIndex.coerceAtMost(book.chapters.lastIndex).coerceAtLeast(0)
        while (cursor >= 0) {
            // 本地书扫描不再设 400 章 / 4 秒上限，命中数必须覆盖当前阅读位置之前的全部章节
            if (scanned >= book.chapters.size) break
            val chapter = book.chapters[cursor]
            scanned++
            val index = cursor
            cursor--

            if (frontMatter.matches(chapter.title.trim())) continue
            val text = chapterText(book, index) ?: continue
            fetched++
            val content = if (index == fromIndex && upToPos > 0) text.take(upToPos.coerceAtMost(text.length)) else text

            // 每章上限只用于防止极端文本，不再人为截断到 6 处
            val positions = findPositions(content, query, perChapterLimit)
            if (positions.isNotEmpty()) {
                earliest = index
                earliestTitle = chapter.title
                positions.forEach { p ->
                    val start = p + query.length
                    val tail = if (start < content.length) content.substring(start, minOf(content.length, start + 3)) else ""
                    hits.add(TraceHit(index, chapter.title, p, excerpt(content, p, query), tail))
                }
            }
        }

        if (hits.isEmpty()) {
            val suggestion = if (query.length >= 3) {
                fuzzySuggestion(book, fromIndex, query, started + 4000)
            } else null
            return ScanResult(query, -1, "", 0, emptyList(), scanned, fetched, truncated, suggestion, anchorChapter = fromIndex)
        }
        val (kept, longName, excluded) = splitAlias(hits, query, mergeAlias, aliasThreshold)
        val ordered = kept.sortedBy { it.chapterIndex }
        return ScanResult(
            query = query,
            firstChapterIndex = earliest,
            firstChapterTitle = earliestTitle,
            hitCount = ordered.size,
            sampled = sample(ordered, sampleMax),
            scanned = scanned,
            fetched = fetched,
            truncated = truncated,
            anchorChapter = fromIndex,
            aliasLong = longName,
            aliasExcluded = excluded,
        )
    }

    /** 错字/多字兜底：找与查询仅差一个字的窗口，取出现次数最多的作为候选 */
    private fun fuzzySuggestion(book: LocalBook, fromIndex: Int, query: String, deadline: Long): String? {
        val counts = HashMap<String, Int>()
        var cursor = fromIndex.coerceAtMost(book.chapters.lastIndex)
        var scanned = 0
        while (cursor >= 0 && scanned < 300 && System.currentTimeMillis() < deadline) {
            val text = store.chapterText(book, cursor)
            cursor--
            scanned++
            if (text.isNullOrEmpty()) continue
            val n = query.length
            var i = 0
            while (i + n <= text.length) {
                var mismatch = 0
                var j = 0
                while (j < n) {
                    if (text[i + j] != query[j]) {
                        mismatch++
                        if (mismatch > 1) break
                    }
                    j++
                }
                if (mismatch <= 1) {
                    val w = text.substring(i, i + n)
                    if (w.all { it in '\u4e00'..'\u9fff' }) counts[w] = (counts[w] ?: 0) + 1
                }
                i++
            }
        }
        return counts.entries.maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key
    }

    /** 找出"更长的名字"（如 伊芙 → 伊芙特罗娜），并把嵌在长名里的匹配单独剔出来 */
    private fun splitAlias(hits: List<TraceHit>, query: String, merge: Boolean, threshold: Int): Triple<List<TraceHit>, String?, Int> {
        // 已经是完整人名（如“鲍德温四世”）时不做长名拆分，避免误吞命中
        if (merge || query.length < 2 || query.length >= 4) return Triple(hits, null, 0)
        val counts = HashMap<String, Int>()
        val particles = "的是在和与了为对向从又也都就才却并而且但很最更曾已将被把让使给用说道问答这那其此他她它们"
        hits.forEach { h ->
            for (k in 1..minOf(3, h.tail.length)) {
                val suffix = h.tail.take(k)
                // 只把「中文续写」当作长名，标点/空格/数字不是别名
                if (suffix.any { it !in '\u4e00'..'\u9fff' }) break
                // 「的」「是」「在」等虚词开头不是名字的一部分
                if (suffix.first() in particles) break
                counts[suffix] = (counts[suffix] ?: 0) + 1
            }
        }
        val best = counts.entries.maxByOrNull { it.value }
        val suffix = best?.takeIf { it.value >= threshold }?.key ?: return Triple(hits, null, 0)
        if (suffix.isBlank()) return Triple(hits, null, 0)
        val longName = query + suffix
        val excluded = hits.filter { it.tail.startsWith(suffix) }
        if (excluded.isEmpty() || excluded.size >= hits.size) return Triple(hits, null, 0)
        return Triple(hits - excluded.toSet(), longName, excluded.size)
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
        if (hits.size <= max || max <= 1) return hits
        val out = LinkedHashMap<Int, TraceHit>()
        val step = (hits.size - 1).toDouble() / (max - 1)
        for (i in 0 until max) {
            val idx = (i * step).toInt().coerceIn(0, hits.lastIndex)
            out[idx] = hits[idx]
        }
        return out.values.toList().sortedBy { it.chapterIndex }
    }
}
