package com.eread.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 传记式详细档案：分段提炼 → 归并 → 成文。
 * 只使用当前阅读位置之前的内容，避免剧透。
 */
class BiographyBuilder(
    private val store: Store,
    private val usage: UsageStore,
    private val localStore: LocalStore,
    private val api: LegadoApi,
) {

    suspend fun build(
        book: BookInfo,
        name: String,
        anchorChapter: Int,
        importance: String,
        onProgress: (Int, Int, String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val llm = Llm(store, usage)

        val readChars = max(1, anchorChapter) * 3000
        val scale = (readChars / 800000f).coerceIn(0.2f, 1f)
        val target = (Importance.bioLimit(importance) * scale).toInt().coerceAtLeast(600)

        onProgress(0, 1, "收集原文…")
        val segments = collectSegments(book, name, anchorChapter)
        if (segments.isEmpty()) error("已读部分里找不到「" + name + "」的段落")

        val totalSteps = min(segments.size, 25) + 2
        val capped = if (segments.size > 25) {
            (0 until 25).map { i -> segments[(i * (segments.size - 1) / 24.0).toInt()] }
        } else segments

        val notes = ArrayList<String>()
        var lastError: Throwable? = null
        capped.forEachIndexed { index, seg ->
            onProgress(index + 1, totalSteps, "分段提炼 " + (index + 1) + "/" + capped.size)
            val prompt = buildString {
                append("下面是小说中与「").append(name).append("」相关的原文（").append(seg.first).append("）。\n")
                append(seg.second.take(9000)).append("\n\n")
                append("请提炼这段里关于此人的事实：身份、行为、关系变化、关键事件。\n")
                append("用 3-6 条短句，每条不超过 60 字；保留章节标记；只写原文有依据的内容；不要评价。")
            }
            var note = runCatching { llm.complete(prompt, "档案·分段", 1500) }
                .onFailure { lastError = it }
                .getOrDefault("")
            if (note.isBlank()) {
                note = runCatching { llm.complete(prompt, "档案·分段", 2500) }
                    .onFailure { lastError = it }
                    .getOrDefault("")
            }
            if (note.isNotBlank()) notes.add(note.trim())
        }
        if (notes.isEmpty()) error("分段提炼没有产出：" + (lastError?.message ?: "未知"))

        var merged = notes.joinToString("\n")
        while (merged.length > 14000) {
            onProgress(capped.size + 1, totalSteps, "压缩中间稿…")
            val groups = merged.chunked(7000).take(6)
            val next = ArrayList<String>()
            groups.forEachIndexed { i, g ->
                val prompt = "把下面的资料合并为要点，保留人物行为与关系，去掉重复，控制在 600 字内：\n" + g
                runCatching { llm.complete(prompt, "档案·归并", 1500) }.getOrNull()?.let { next.add(it) }
                onProgress(capped.size + 1, totalSteps, "压缩 " + (i + 1) + "/" + groups.size)
            }
            if (next.isEmpty()) break
            merged = next.joinToString("\n")
        }

        onProgress(totalSteps, totalSteps, "撰写档案（约 " + target + " 字）…")
        val finalPrompt = buildString {
            append("请根据以下资料，为人物「").append(name).append("」写一篇传记式档案。\n\n")
            append(merged.take(14000)).append("\n\n")
            append("要求：\n")
            append("1. 约 ").append(target).append(" 字，不要超过；\n")
            append("2. 分小标题：出身与身份 / 关键经历 / 人物关系 / 现状；\n")
            append("3. 只写资料里有依据的内容，不要虚构；资料不足的部分写“已读部分未交代”；\n")
            append("4. 关键事件后用 [第N项] 标注出处；\n")
            append("5. 文风凝练，有叙事感，不要罗列要点。")
        }
        val maxTokens = (target * 2).toInt().coerceIn(2500, 16000)
        llm.complete(finalPrompt, "详细档案", maxTokens).trim().ifEmpty { error("模型返回为空") }
    }

    private fun collectSegments(book: BookInfo, name: String, anchorChapter: Int): List<Pair<String, String>> {
        val local = runCatching { localStore.findMatching(book.name) }.getOrNull()
        val segments = ArrayList<Pair<String, String>>()
        val buffer = StringBuilder()
        var startLabel = ""
        var lastLabel = ""

        fun flush() {
            if (buffer.length > 400) segments.add((startLabel + "—" + lastLabel) to buffer.toString())
            buffer.setLength(0)
            startLabel = ""
        }

        if (local != null) {
            val chapters = runCatching { api.chapters(book.bookUrl) }.getOrDefault(emptyList())
            val from = if (chapters.isEmpty()) anchorChapter else {
                val idx = (anchorChapter.toFloat() / chapters.size * local.chapters.size).toInt()
                idx
            }
            val end = min(from, local.chapters.lastIndex)
            for (i in 0..end) {
                val ch = local.chapters[i]
                val text = runCatching { localStore.chapterText(local, i) }.getOrNull() ?: continue
                val picked = extract(text, name)
                if (picked.isBlank()) continue
                if (startLabel.isEmpty()) startLabel = "第 " + (i + 1) + " 项"
                lastLabel = "第 " + (i + 1) + " 项"
                buffer.append(picked).append("\n")
                if (buffer.length > 12000) flush()
            }
        } else {
            val chapters = runCatching { api.chapters(book.bookUrl) }.getOrDefault(emptyList())
            val end = min(anchorChapter, chapters.lastIndex)
            if (end >= 0) {
                for (i in 0..min(end, 300)) {
                    val text = runCatching { api.content(book.bookUrl, chapters[i].index) }.getOrNull() ?: continue
                    val picked = extract(text, name)
                    if (picked.isBlank()) continue
                    if (startLabel.isEmpty()) startLabel = "第 " + (i + 1) + " 项"
                    lastLabel = "第 " + (i + 1) + " 项"
                    buffer.append(picked).append("\n")
                    if (buffer.length > 12000) flush()
                }
            }
        }
        flush()
        return segments
    }

    /** 只取提到该人物的段落，控制单章体量 */
    private fun extract(text: String, name: String): String {
        if (!text.contains(name)) return ""
        val paras = text.split('\n')
        val kept = paras.filter { it.contains(name) }
        if (kept.isEmpty()) return ""
        return kept.joinToString("\n").take(8000)
    }
}
