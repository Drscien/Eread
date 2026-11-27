package com.eread.companion

/** 一本书的人名实体索引：全名、基名、世次、出现证据。 */
data class NameEntity(
    val full: String,
    val base: String,
    val regnal: String,
    val segments: List<String>,
    val latin: String = "",
    var count: Int = 0,
    var firstChapter: Int = 0,
    var firstPos: Int = 0,
) {
    val displayName: String get() = full
}

sealed class NameResolution {
    data class Unique(val entity: NameEntity, val auto: Boolean) : NameResolution()
    data class Ambiguous(val query: String, val candidates: List<NameEntity>) : NameResolution()
    object NotFound : NameResolution()
}

class NameIndex(val entities: List<NameEntity>) {

    private val byFull: Map<String, NameEntity> = entities.associateBy { it.full }
    private val byBase: Map<String, List<NameEntity>> = entities.groupBy { it.base }

    fun resolve(query: String): NameResolution {
        val parsed = NameParser.parse(query) ?: return NameResolution.NotFound
        byFull[parsed.full]?.let { return NameResolution.Unique(it, auto = false) }

        val lower = parsed.full.lowercase()

        // 英文名精确命中：拉丁名相同则直接采用，并优先中文实体
        val latinExact = entities.filter { it.latin.isNotBlank() && it.latin.equals(parsed.full, ignoreCase = true) }
        if (latinExact.size == 1) {
            return NameResolution.Unique(latinExact[0], auto = latinExact[0].full != parsed.full)
        }
        if (latinExact.size > 1) {
            return NameResolution.Ambiguous(parsed.full, preferChinese(latinExact).take(8))
        }

        val candidates = entities.filter { e ->
            e.full == parsed.full ||
                isPrefixAtBoundary(e.full, parsed.full) ||
                e.base.startsWith(parsed.full) ||
                e.base == parsed.full ||
                (e.latin.isNotBlank() && (e.latin.lowercase() == lower || e.latin.lowercase().startsWith(lower + " ")))
        }
        val merged = preferChinese(candidates)

        return when {
            merged.isEmpty() -> NameResolution.NotFound
            merged.size == 1 -> NameResolution.Unique(merged[0], auto = merged[0].full != parsed.full)
            else -> NameResolution.Ambiguous(parsed.full, merged.sortedByDescending { it.count }.take(8))
        }
    }

    /** 同一拉丁名存在中英文两条时，只保留中文实体，避免候选重复。 */
    private fun preferChinese(list: List<NameEntity>): List<NameEntity> {
        val map = LinkedHashMap<String, NameEntity>()
        list.forEach { e ->
            val key = if (e.latin.isNotBlank()) e.latin.lowercase() else e.full
            val old = map[key]
            val eIsChinese = e.full.any { it.code in 0x4e00..0x9fff }
            val oldIsChinese = old != null && old.full.any { it.code in 0x4e00..0x9fff }
            if (old == null || (!oldIsChinese && eIsChinese)) map[key] = e
        }
        return map.values.toList()
    }

    /** query 必须落在段边界：全名以 query 开头，且剩余部分以 · 开头或是“世”。 */
    private fun isPrefixAtBoundary(full: String, query: String): Boolean {
        if (!full.startsWith(query)) return false
        val rest = full.removePrefix(query)
        if (rest.isEmpty()) return true
        return rest.startsWith("·") || rest.matches(Regex("(十[一二]?|[一二三四五六七八九十])世"))
    }
}
