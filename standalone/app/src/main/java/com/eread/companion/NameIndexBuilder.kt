package com.eread.companion

/** 从已有人名种子出发，在本书正文里发现完整写法（含世次、· 结构）并计数。 */
object NameIndexBuilder {

    private val cache = HashMap<String, NameIndex>()

    fun get(
        local: LocalBook,
        seeds: List<String>,
        preload: List<NameEntity> = emptyList(),
        textOf: (Int) -> String?,
    ): NameIndex {
        val key = local.fileName + "#" + local.chapters.size + "#" + seeds.sorted().joinToString(",").hashCode()
        synchronized(cache) { cache[key]?.let { return it } }
        val index = build(local, seeds, preload, textOf)
        synchronized(cache) { cache[key] = index }
        return index
    }

    fun invalidate() = synchronized(cache) { cache.clear() }

    private const val NAME_STOP_CHARS = "的是在和与了为对向从又也都就才却并而且但很最更曾已将被把让使给用说道问答家王帝长爵军师者"

    private val latinBlockWords = setOf(
        "press", "university", "history", "illustrated", "vol", "volume", "order",
        "military", "palazzo", "museum", "society", "journal", "edition", "translated",
        "london", "oxford", "cambridge", "york", "isbn", "crusades", "knights",
        "hospitaller", "hospitallers", "east", "kingdom", "latin", "greek", "arabic",
        "syriac", "armenian", "chapter", "note",
    )

    private val latinStopWords = setOf(
        "The", "This", "That", "These", "Those", "Chapter", "Part", "Book",
        "Figure", "Table", "Note", "Appendix", "Volume", "Section",
    )

    /** 全书无种子建索引：只收高置信度写法（·结构、世次、多词拉丁名、拉丁括号对照）。 */
    fun full(
        local: LocalBook,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
        textOf: (Int) -> String?,
    ): NameIndex {
        val found = LinkedHashMap<String, NameEntity>()
        val total = local.chapters.size
        val skipTitles = Regex("^(参考文献|参考书目|索引|致谢|版权|contents|目录|reference|bibliography|index)$", RegexOption.IGNORE_CASE)
        for (ch in 0 until total) {
            val chapterTitle = local.chapters.getOrNull(ch)?.title?.trim().orEmpty()
            if (skipTitles.matches(chapterTitle)) {
                onProgress(ch + 1, total)
                continue
            }
            val text = textOf(ch)
            if (text.isNullOrBlank()) {
                onProgress(ch + 1, total)
                continue
            }
            Regex("[^\\s·，。、；：！？（）()" + NAME_STOP_CHARS + "]{1,6}(?:·[^\\s·，。、；：！？（）()" + NAME_STOP_CHARS + "]{1,5}){1,3}(?:[一二三四五六七八九十]{1,3}世)?")
                .findAll(text).take(40).forEach { m ->
                    if (isCitationLine(text, m.range.first)) return@forEach
                    val parsed = parseNameWithTail(m.value) ?: return@forEach
                    add(found, parsed, ch, m.range.first)
                }
            Regex("([\\u4e00-\\u9fff]{2,6})(十[一二]?|[一二三四五六七八九十])世")
                .findAll(text).take(40).forEach { m ->
                    val base = m.groupValues[1].trimStart('的', '和', '与', '是', '在', '了', '为', '对', '向', '从')
                    if (base.length < 2) return@forEach
                    if (isCitationLine(text, m.range.first)) return@forEach
                    val parsed = NameParser.parse(base + m.groupValues[2] + "世") ?: return@forEach
                    add(found, parsed, ch, m.range.first)
                }
            Regex("[A-Z][a-z]{2,}(?:(?:\\s+(?:de|du|di|da|van|von|of)?\\s*[A-Z][a-z]{2,}){1,3}|\\s+(?:VIII|VII|VI|IV|IX|III|II|X|V|I))")
                .findAll(text).take(60).forEach { m ->
                    val raw = m.value.trim()
                    if (raw.substringBefore(' ') in latinStopWords) return@forEach
                    if (isCitationLine(text, m.range.first)) return@forEach
                    val parsed = NameParser.parse(raw) ?: return@forEach
                    add(found, parsed, ch, m.range.first)
                }
            Regex("([\\u4e00-\\u9fff·]{2,20})\\s*[（(]\\s*([A-Za-z][A-Za-z .'\\-]{2,40})\\s*[）)]")
                .findAll(text).take(20).forEach { pair ->
                    val cn = NameParser.parse(NameParser.cleanDescriptor(pair.groupValues[1])) ?: return@forEach
                    val latin = pair.groupValues[2].trim()
                    val existing = found[cn.full]
                    if (existing == null) {
                        found[cn.full] = NameEntity(cn.full, cn.base, cn.regnal, cn.segments, latin, count = 1)
                    } else if (existing.latin.isBlank()) {
                        found[cn.full] = existing.copy(latin = latin)
                    }
                }
            onProgress(ch + 1, total)
            if (found.size > 600 && ch > total * 7 / 10) break
        }
        val entities = found.values.filter { e ->
            val chinese = e.full.any { it.code in 0x4e00..0x9fff }
            val dotted = e.segments.size > 1
            val roleChars = "家王帝长爵军师者督"
            val badChinese = dotted && (
                e.base.length !in 2..4 ||
                    e.segments.any { seg -> seg.isNotEmpty() && seg.last() in roleChars }
                )
            val latinWords = e.full.lowercase().split(Regex("\\s+"))
            val badLatin = !chinese && latinWords.any { it.trim('.') in latinBlockWords }
            if (badChinese || badLatin) return@filter false
            val strong = e.regnal.isNotEmpty() || e.latin.isNotBlank() || e.count >= 3
            strong || (dotted && e.count >= 2)
        }.take(400)
        return NameIndex(entities.sortedByDescending { it.count })
    }

    private val tailParticles = "是的在和与了为对向从又也都就才却并而且但很最更曾已将被把让使给用说道问答".toSet()

    private val leadParticles = "后来了又也都就才却并而且但很最更曾已将被把让使给用是的在和与为对向从".toSet()

    private val roleSuffixes = listOf(
        "是团长", "大团长", "团长", "是国王", "国王", "是伯爵", "伯爵", "是公爵", "公爵",
        "是主教", "主教", "是教皇", "教皇", "是将军", "将军", "作者", "译者", "家族",
    )

    private fun parseNameWithTail(raw: String): NameParser.Parsed? {
        var text = raw.trim()
        if (text.length > 24) return null
        roleSuffixes.forEach { suffix ->
            if (text.length > suffix.length + 2 && text.endsWith(suffix)) {
                text = text.removeSuffix(suffix)
            }
        }
        while (text.length > 2 && text.last() in tailParticles) {
            text = text.dropLast(1)
        }
        while (text.length > 2 && text.first() in leadParticles && text.contains('·')) {
            text = text.drop(1)
        }
        return NameParser.parse(text)
    }

    private fun isCitationLine(text: String, pos: Int): Boolean {
        val start = (pos - 40).coerceAtLeast(0)
        val end = (pos + 40).coerceAtMost(text.length)
        val line = text.substring(start, end)
        return line.contains("出版社") || line.contains("年版") || line.contains("页") ||
            line.contains('[') || line.contains("trans.") || line.contains("ed.")
    }

    private fun add(target: LinkedHashMap<String, NameEntity>, parsed: NameParser.Parsed, chapter: Int, pos: Int) {
        val entity = target.getOrPut(parsed.full) {
            NameEntity(
                full = parsed.full,
                base = parsed.base,
                regnal = parsed.regnal,
                segments = parsed.segments,
                latin = parsed.latin,
                firstChapter = chapter,
                firstPos = pos,
            )
        }
        entity.count++
    }

    private fun build(
        local: LocalBook,
        seeds: List<String>,
        preload: List<NameEntity>,
        textOf: (Int) -> String?,
    ): NameIndex {
        val found = LinkedHashMap<String, NameEntity>()
        preload.take(500).forEach { found[it.full] = it }
        val parsedSeeds = seeds.mapNotNull { NameParser.parse(it) }.distinctBy { it.full }
        if (parsedSeeds.isEmpty()) return NameIndex(emptyList())
        if (preload.isNotEmpty() && parsedSeeds.all { seed ->
                preload.any { e -> e.base == seed.base || e.full.startsWith(seed.base) }
            }
        ) {
            return NameIndex(preload)
        }

        val maxChapters = minOf(local.chapters.size, 120)
        for (ch in 0 until maxChapters) {
            val text = textOf(ch) ?: continue
            // 中文（Latin）配对
            Regex("([\\u4e00-\\u9fff·]{2,20})\\s*[（(]\\s*([A-Za-z][A-Za-z .'\\-]{2,40})\\s*[）)]")
                .findAll(text).take(20).forEach { pair ->
                    val cn = NameParser.parse(pair.groupValues[1]) ?: return@forEach
                    val latinName = pair.groupValues[2].trim()
                    parsedSeeds.filter { it.base == cn.base || it.full == cn.full }.forEach { seed ->
                        val existing = found[cn.full]
                        if (existing != null) {
                            if (existing.latin.isBlank()) found[cn.full] = existing.copy(latin = latinName)
                        } else {
                            found[cn.full] = NameEntity(
                                full = cn.full,
                                base = cn.base,
                                regnal = cn.regnal,
                                segments = cn.segments,
                                latin = latinName,
                            )
                        }
                    }
                }
            parsedSeeds.forEach { seed ->
                val regnalPart = if (seed.regnal.isEmpty()) "" else "|" + Regex.escape(seed.regnal)
                val isLatin = seed.base.matches(Regex("[A-Za-z][A-Za-z .'\\-]*"))
                val latinPart = if (isLatin) "(?:\\s+(VIII|VII|VI|IV|IX|III|II|X|V|I))?(?![A-Za-z])" else ""
                val pattern = Regex(
                    Regex.escape(seed.base) + "(?:·[\\u4e00-\\u9fff]{1,3}){0,3}" + latinPart +
                        "(?:(十[一二]?|[一二三四五六七八九十])世)?"
                )
                pattern.findAll(text).take(40).forEach { m ->
                    val raw = m.value
                    if (raw.length < 2 || raw.length > 24) return@forEach
                    val parsed = NameParser.parse(raw) ?: return@forEach
                    // 只保留基名相同的扩展，避免把“雷蒙德”误当“雷蒙”的同实体
                    if (parsed.base != seed.base) return@forEach
                    val entity = found.getOrPut(parsed.full) {
                        NameEntity(
                            full = parsed.full,
                            base = parsed.base,
                            regnal = parsed.regnal,
                            segments = parsed.segments,
                            latin = seed.latin,
                            firstChapter = ch,
                            firstPos = m.range.first,
                        )
                    }
                    entity.count++
                }
            }
        }
        val entities = found.values.filter { e ->
            val bare = e.full == e.base && e.regnal.isEmpty() && e.segments.size == 1
            if (!bare) return@filter true
            // 只有“鲍德温”这种裸名时，如果存在同基名的更长写法，就不把裸名当独立实体
            !found.values.any { other ->
                other !== e && other.base.startsWith(e.base) &&
                    (other.base != e.base || other.regnal.isNotEmpty() || other.segments.size > 1)
            }
        }
        return NameIndex(entities.sortedByDescending { it.count })
    }
}
