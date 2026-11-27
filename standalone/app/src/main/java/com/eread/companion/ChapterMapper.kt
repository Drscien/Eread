package com.eread.companion

/** 本地书与 legado 章节序号互转。 */
object ChapterMapper {

    private fun norm(s: String): String = s.replace(Regex("[\\s　]+"), "").trim()

    fun localIndex(local: LocalBook, chapters: List<ChapterInfo>, legadoIndex: Int): Int {
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

    fun legadoIndex(local: LocalBook, chapters: List<ChapterInfo>, localIndex: Int): Int {
        if (chapters.isEmpty()) return localIndex
        val byTitle = HashMap<String, Int>()
        local.chapters.forEach { byTitle[norm(it.title)] = it.index }
        chapters.forEach { c -> if (byTitle[norm(c.title)] == localIndex) return c.index }
        if (chapters.size == local.chapters.size) return localIndex.coerceIn(0, chapters.lastIndex)
        val scaled = (localIndex.toFloat() / local.chapters.size * chapters.size).toInt()
        return scaled.coerceIn(0, chapters.lastIndex)
    }
}
