package com.eread.companion

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 关系网的候选人物、补档、提示词构造。 */
class RelationController(
    private val context: Context,
    private val store: Store,
    private val localStore: LocalStore,
) {

    fun candidates(local: LocalBook?, ocrNames: List<String>): List<Triple<String, String, String>> {
        if (local == null) return dedupe(ocrNames).map { Triple(it, "当前页", "") }
        val state = BookState.load(context, "local://" + local.fileName, local.name)
        val index = runCatching { NameIndex(NameIndexStore.load(context, local)) }.getOrDefault(NameIndex(emptyList()))
        val archive = state.cards.keys.toList()
        val ocr = (if (ocrNames.isNotEmpty()) ocrNames else store.ocrNames(local.name)).toSet()
        // 当前页的短名（如「雷蒙」）可能被档案里的长名（「雷蒙德」）覆盖，
        // 但用户需要在候选里看到它，因此这里不再做前缀去重，只去掉完全相同的重复项。
        val canonical = (archive + ocr.toList()).map { name ->
            val near = NameMatcher.nearest(name, index.entities, store.nameCloseDistance)
            near?.full ?: name
        }.distinct()
        return canonical.map { name ->
            val tag = when {
                name in archive -> "档案"
                name in ocr -> "当前页"
                else -> ""
            }
            Triple(name, tag, state.cards[name]?.latin.orEmpty())
        }
    }

    fun ensureProfiles(local: LocalBook, names: List<String>) {
        if (names.isEmpty()) return
        val state = BookState.load(context, "local://" + local.fileName, local.name)
        var changed = false
        names.forEach { name ->
            if (state.cards[name] == null) {
                state.cards[name] = Card(name, "", 0, System.currentTimeMillis(), 0)
                changed = true
            }
        }
        if (changed) state.save(context)
    }

    /** 为主/次人物补齐最少量的概述，避免只有空档案就生成关系。 */
    suspend fun ensureSummaries(local: LocalBook, names: List<String>) {
        withContext(Dispatchers.IO) {
        val state = BookState.load(context, "local://" + local.fileName, local.name)
        val missing = names.filter { state.cards[it]?.summary.isNullOrBlank() }
        if (missing.isEmpty()) return@withContext
        val index = runCatching { NameIndex(NameIndexStore.load(context, local)) }.getOrDefault(NameIndex(emptyList()))
        val blocks = missing.take(12).map { name ->
            val snippet = evidence(local, index, name)
            name + "：" + if (snippet.isBlank()) "（已读范围内未找到出场）" else snippet.take(400)
        }
        val prompt = "下面是一部小说中的人物及其原文片段。请为每个人物写一句 60 字以内的概述，" +
            "只依据给出的片段，不要编造。每行格式：姓名：概述\n\n" + blocks.joinToString("\n\n")
        val out = runCatching {
            Llm(store, UsageStore(context)).complete(prompt, "人物补档", 900).trim()
        }.getOrNull().orEmpty()
        if (out.isBlank()) return@withContext
        val map = HashMap<String, String>()
        out.lines().forEach { line ->
            val at = line.indexOf('：').takeIf { it > 0 } ?: line.indexOf(':').takeIf { it > 0 } ?: return@forEach
            val name = line.substring(0, at).trim().trimStart('·', '-', '—', '*', ' ')
            val body = line.substring(at + 1).trim()
            if (name.isNotBlank() && body.isNotBlank()) map[name] = body
        }
        val fresh = BookState.load(context, "local://" + local.fileName, local.name)
        var changed = false
        missing.forEach { wanted ->
            val summary = map.entries.firstOrNull { (name, _) ->
                name == wanted || name.startsWith(wanted) || wanted.startsWith(name)
            }?.value ?: evidence(local, index, wanted).take(80).ifBlank { "已读范围内资料有限" }
            val card = fresh.cards[wanted] ?: Card(wanted, "", 0, System.currentTimeMillis(), 0)
            fresh.cards[wanted] = card.copy(summary = summary, updatedAt = System.currentTimeMillis())
            changed = true
        }
        if (changed) fresh.save(context)
        }
    }

    /** 一次扫描全书，找主人物与每位次人物在章内最接近的一次出现。 */
    suspend fun cooccurrences(local: LocalBook, main: String, seconds: List<String>): Map<String, List<String>> =
        withContext(Dispatchers.IO) {
            val found = seconds.associateWith { mutableListOf<String>() }
            if (seconds.isEmpty()) return@withContext emptyMap()
            fun indices(text: String, needle: String, limit: Int = 30): List<Int> {
                val out = ArrayList<Int>()
                var from = 0
                while (out.size < limit) {
                    val at = text.indexOf(needle, from)
                    if (at < 0) break
                    out.add(at)
                    from = at + needle.length
                }
                return out
            }
            var chapter = 0
            while (chapter < local.chapters.size) {
                if (found.values.all { it.size >= store.cooccurSnippets }) break
                val text = localStore.chapterText(local, chapter)
                chapter++
                if (text.isNullOrBlank()) continue
                val mains = indices(text, main)
                if (mains.isEmpty()) continue
                val title = local.chapters.getOrNull(chapter - 1)?.title.orEmpty()
                for (sec in seconds) {
                    val list = found[sec] ?: continue
                    if (list.size >= store.cooccurSnippets) continue
                    val secs = indices(text, sec)
                    if (secs.isEmpty()) continue
                    var bestMain = 0
                    var bestSec = 0
                    var bestGap = Int.MAX_VALUE
                    mains.forEach { m ->
                        secs.forEach { x ->
                            val gap = kotlin.math.abs(x - m)
                            if (gap < bestGap) {
                                bestGap = gap
                                bestMain = m
                                bestSec = x
                            }
                        }
                    }
                    if (bestGap > store.cooccurDistance) continue
                    val begin = (minOf(bestMain, bestSec) - 140).coerceAtLeast(0)
                    val end = (maxOf(bestMain + main.length, bestSec + sec.length) + 160).coerceAtMost(text.length)
                    list.add("（" + title + "）" + text.substring(begin, end).replace('\n', ' '))
                }
            }
            found.filterValues { it.isNotEmpty() }
        }

    fun buildPrompt(
        local: LocalBook,
        main: String,
        seconds: List<String>,
        pairs: Map<String, List<String>> = emptyMap(),
    ): String {
        val state = BookState.load(context, "local://" + local.fileName, local.name)
        val index = runCatching { NameIndex(NameIndexStore.load(context, local)) }.getOrDefault(NameIndex(emptyList()))
        fun desc(name: String): String {
            val c = state.cards[name]
            val body = c?.let { if (it.biography.isNotBlank()) it.biography else it.summary }.orEmpty()
            val snippet = if (body.length < 60) evidence(local, index, name) else ""
            val pair = pairs[name].orEmpty()
            if (body.isBlank() && snippet.isBlank() && pair.isEmpty()) return name + "：暂无档案与原文片段"
            val sb = StringBuilder(name).append("：").append(body.take(220))
            if (snippet.isNotBlank()) sb.append("\n原文片段：").append(snippet)
            if (pair.isNotEmpty()) {
                sb.append("\n与").append(main).append("的共现片段：").append(pair.joinToString(" / ").take(700))
            }
            return sb.toString()
        }
        val sb = StringBuilder()
        sb.append("以下是小说《").append(local.name).append("》的人物资料（仅含已读部分）。\n")
        sb.append(desc(main)).append("\n")
        seconds.take(50).forEach { sb.append(desc(it)).append("\n") }
        sb.append("\n请只输出主人物「").append(main).append("」与下列次人物的关系，每位一行，顺序如下：\n")
        seconds.take(50).forEach { sb.append("· ").append(it).append("\n") }
        sb.append("格式：次人物 —— 关系说明（一句话，不超过 30 字）。\n")
        sb.append("只能使用上面列出的次人物，不得增加、替换或补充其他人物；")
        sb.append("请优先依据「共现片段」判断直接关系；没有共现片段时再看人物资料与各自片段。")
        sb.append("无法判断时统一写「资料片段不详」，不要写其他占位句。")
        sb.append("不要分类、不要标题、不要开场白。")
        return sb.toString()
    }

    /** 档案为空时，从首次出现章节附近取两段原文，供模型判断关系。 */
    private fun evidence(local: LocalBook, index: NameIndex, name: String): String {
        val entity = index.entities.firstOrNull { it.full == name || it.base == name }
        val start = (entity?.firstChapter ?: 0).coerceIn(0, local.chapters.lastIndex.coerceAtLeast(0))
        val out = StringBuilder()
        var found = 0
        var chapter = start
        while (chapter < local.chapters.size && chapter < start + store.evidenceChapters && found < 2) {
            val text = localStore.chapterText(local, chapter)
            chapter++
            if (text.isNullOrBlank()) continue
            var from = 0
            while (found < 2) {
                val at = text.indexOf(name, from)
                if (at < 0) break
                val begin = (at - 60).coerceAtLeast(0)
                val end = (at + name.length + 120).coerceAtMost(text.length)
                out.append(text.substring(begin, end).replace('\n', ' ')).append(" … ")
                found++
                from = at + name.length
            }
        }
        return out.toString().trim()
    }

    private fun dedupe(input: List<String>): List<String> {
        val sorted = input.distinct().sortedByDescending { it.length }
        val out = ArrayList<String>()
        for (name in sorted) {
            val covered = out.any { longer -> longer.startsWith(name) && longer.length - name.length <= 2 }
            if (!covered) out.add(name)
        }
        return out.sortedBy { it }
    }
}
