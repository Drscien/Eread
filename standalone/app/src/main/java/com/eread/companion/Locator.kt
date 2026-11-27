package com.eread.companion

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * OCR 文本对回本地书：分块锚定 + 编辑距离校验 + 偏移共识。
 * 与“逐字对位”相比，能容忍 OCR 漏字/多字造成的整体错位。
 */
object Locator {

    data class Hit(val chapter: Int, val pos: Int, val score: Double)

    private val keep = Regex("[^\\u4e00-\\u9fff\\u3400-\\u4dbfA-Za-z0-9]")
    private data class NormText(val text: String, val rawIndex: IntArray)

    private val cache = object : LinkedHashMap<String, NormText>(12, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, NormText>?): Boolean = size > 12
    }

    private fun chapterNormalized(store: LocalStore?, book: LocalBook, ch: Int, textOf: ((Int) -> String?)? = null): NormText {
        if (textOf != null) return normalizeWithMap(textOf(ch).orEmpty())
        val key = book.fileName + "#" + ch
        synchronized(cache) { cache[key]?.let { return it } }
        val text = normalizeWithMap(runCatching { store?.chapterText(book, ch) }.getOrNull() ?: "")
        synchronized(cache) { cache[key] = text }
        return text
    }

    /** 归一化并记录每个归一化字符对应的原始章节下标，供 Hit.pos 使用。 */
    private fun normalizeWithMap(raw: String): NormText {
        val sb = StringBuilder(raw.length)
        val map = ArrayList<Int>(raw.length)
        for (i in raw.indices) {
            val c = raw[i].lowercaseChar()
            val keepChar = c in 'a'..'z' || c in '0'..'9' || c in '\u4e00'..'\u9fff' || c in '\u3400'..'\u4dbf'
            if (keepChar) {
                sb.append(c)
                map.add(i)
            }
        }
        return NormText(sb.toString(), map.toIntArray())
    }
    private const val CHUNK = 24
    private const val STEP = 12

    fun normalize(raw: String): String = raw.replace(keep, "").lowercase()

    fun ranked(
        book: LocalBook,
        ocr: String,
        hintChapter: Int,
        store: LocalStore?,
        limit: Int = 5,
        textOf: ((Int) -> String?)? = null,
        candidateRange: Int = 20,
    ): List<Hit> {
        val q = normalize(ocr)
        if (q.length < 60) return emptyList()
        val chunks = ArrayList<Pair<Int, String>>()
        var p = 0
        while (p + CHUNK <= q.length && chunks.size < 24) {
            chunks.add(p to q.substring(p, p + CHUNK))
            p += STEP
        }
        if (chunks.size < 3) return emptyList()

        val out = ArrayList<Hit>()
        for (ch in candidates(book.chapters.size, hintChapter, candidateRange)) {
            val normalized = chapterNormalized(store, book, ch, textOf)
            val text = normalized.text
            if (text.length < 80) continue
            val offsets = ArrayList<Int>()
            var matchedChunks = 0
            for ((posInQ, chunk) in chunks) {
                val found = findChunkAll(text, chunk)
                if (found.isEmpty()) continue
                matchedChunks++
                found.forEach { at -> offsets.add(at - posInQ) }
            }
            if (matchedChunks < 3) continue
            val score = matchedChunks.toDouble() / chunks.size
            val bestNorm = bestOffset(text, q, offsets) ?: continue
            // 归一化下标换回原始章节下标，避免后续按原始文本截断时错位
            val rawPos = if (bestNorm in normalized.rawIndex.indices) normalized.rawIndex[bestNorm] else normalized.rawIndex.lastOrNull()?.plus(1) ?: 0
            out.add(Hit(ch, max(0, rawPos), score))
            if (score >= 0.9 && similarity(q, text, bestNorm) >= 0.8) break
        }
        return out.sortedByDescending { it.score }.take(limit)
    }

    fun locate(
        book: LocalBook,
        ocr: String,
        hintChapter: Int,
        store: LocalStore?,
        textOf: ((Int) -> String?)? = null,
        candidateRange: Int = 20,
    ): Hit? = ranked(book, ocr, hintChapter, store, 5, textOf, candidateRange).firstOrNull()?.takeIf { it.score >= 0.22 }

    private fun candidates(size: Int, hint: Int, range: Int = 20): List<Int> {
        if (size <= 0) return emptyList()
        val out = LinkedHashSet<Int>()
        val h = hint.coerceIn(0, size - 1)
        out.add(h)
        for (d in 1..range) {
            if (h - d >= 0) out.add(h - d)
            if (h + d < size) out.add(h + d)
        }
        var i = 0
        while (out.size < 80 && i < size) {
            out.add(i)
            i += max(1, size / 50)
        }
        return out.toList()
    }

    /** 同一分块可能在前文重复出现，返回最多 4 个近似匹配起点。 */
    private fun findChunkAll(text: String, chunk: String): List<Int> {
        val out = ArrayList<Int>(4)
        var from = 0
        var guard = 0
        while (out.size < 4 && guard < 40) {
            guard++
            var idx = -1
            for (seedOff in intArrayOf(0, 8, 16)) {
                if (seedOff + 8 > chunk.length) continue
                val seed = chunk.substring(seedOff, seedOff + 8)
                idx = text.indexOf(seed, from)
                if (idx >= 0) break
            }
            if (idx < 0) break
            for (seedOff in intArrayOf(0, 8, 16)) {
                val start = idx - seedOff
                if (start >= 0 && start + chunk.length <= text.length && distance(chunk, text, start) <= 5) {
                    if (out.none { kotlin.math.abs(it - start) <= 3 }) out.add(start)
                    break
                }
            }
            from = idx + 1
        }
        return out
    }

    /** 候选偏移按全文相似度复核，避免被前文重复段落带偏。 */
    private fun bestOffset(text: String, q: String, offsets: List<Int>): Int? {
        if (offsets.isEmpty()) return null
        val sorted = offsets.distinct().sorted()
        val clusters = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < sorted.size) {
            var j = i
            while (j < sorted.size && sorted[j] - sorted[i] <= 8) j++
            val count = j - i
            if (count >= 3) clusters.add(((sorted[i] + sorted[j - 1]) / 2) to count)
            i = j
        }
        if (clusters.isEmpty()) return consensusOffset(offsets)
        var bestCenter = clusters[0].first
        var bestRank = -1.0
        clusters.forEach { (center, count) ->
            val sim = similarity(q, text, center)
            val rank = sim * 0.75 + (count.toDouble() / offsets.size) * 0.25
            if (rank > bestRank) {
                bestRank = rank
                bestCenter = center
            }
        }
        return bestCenter
    }

    private fun similarity(q: String, text: String, start: Int): Double {
        var best = 0
        for (shift in -6..6) {
            val s = start + shift
            if (s < 0 || s >= text.length) continue
            var same = 0
            var i = 0
            while (i < q.length && s + i < text.length) {
                if (q[i] == text[s + i]) same++
                i++
            }
            if (same > best) best = same
        }
        return best.toDouble() / q.length.coerceAtLeast(1)
    }

    /** 带宽受限的编辑距离：比较 chunk 与 text[start, start+len)，允许 ±2 的起点漂移。 */
    private fun distance(chunk: String, text: String, start: Int): Int {
        var best = chunk.length
        for (shift in -2..2) {
            val s = start + shift
            if (s < 0 || s + chunk.length > text.length) continue
            var cost = 0
            for (i in chunk.indices) {
                if (chunk[i] != text[s + i]) cost++
                if (cost >= best) break
            }
            if (cost < best) best = cost
        }
        return best
    }

    /** 多个分块推出来的起点应聚在一起；取最大簇的中心。 */
    private fun consensusOffset(offsets: List<Int>): Int? {
        val sorted = offsets.sorted()
        var bestCount = 0
        var bestCenter = 0
        for (i in sorted.indices) {
            var j = i
            while (j < sorted.size && sorted[j] - sorted[i] <= 6) j++
            val count = j - i
            if (count > bestCount) {
                bestCount = count
                bestCenter = (sorted[i] + sorted[j - 1]) / 2
            }
        }
        return if (bestCount >= 3) bestCenter else null
    }
}
