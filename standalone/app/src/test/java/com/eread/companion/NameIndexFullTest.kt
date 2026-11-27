package com.eread.companion

import org.junit.Assert.assertTrue
import org.junit.Test

class NameIndexFullTest {

    private fun book(chapters: List<String>): LocalBook = LocalBook(
        "测试", "full-index.txt",
        chapters.indices.map { LocalChapter(it, "第" + (it + 1) + "节", 0L, 0L) },
    )

    @Test
    fun findsEnglishNamesWithoutSeeds() {
        val text = "Baldwin III was king. Baldwin IV was his nephew. Jacques de Vitry wrote about it."
        val index = NameIndexBuilder.full(book(listOf(text))) { i -> if (i == 0) text else null }
        val fulls = index.entities.map { it.full }.toSet()
        assertTrue(fulls.toString(), fulls.any { it.contains("Baldwin III") })
        assertTrue(fulls.toString(), fulls.any { it.contains("Baldwin IV") })
        assertTrue(fulls.toString(), fulls.any { it.contains("Jacques de Vitry") })
    }

    @Test
    fun findsChineseDotAndRegnalNames() {
        val text = "雷蒙·杜·皮伊是团长。后来雷蒙·杜·皮伊又出现。鲍德温三世与鲍德温四世都是国王。"
        val index = NameIndexBuilder.full(book(listOf(text))) { i -> if (i == 0) text else null }
        val fulls = index.entities.map { it.full }.toSet()
        assertTrue(fulls.toString(), fulls.contains("雷蒙·杜·皮伊"))
        assertTrue(fulls.toString(), fulls.contains("鲍德温三世"))
        assertTrue(fulls.toString(), fulls.contains("鲍德温四世"))
    }
}
