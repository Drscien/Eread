package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameIndexTest {

    @org.junit.Before
    fun clear() {
        NameIndexBuilder.invalidate()
    }

    private fun book(chapters: List<String>): LocalBook = LocalBook(
        "测试书",
        "test-name-index.txt",
        chapters.indices.map { LocalChapter(it, "第" + (it + 1) + "节", 0L, 0L) },
    )

    @Test
    fun pairsChineseWithLatin() {
        val text = "雅克·德·维特里（Jacques de Vitry）是法国神学家。"
        val index = NameIndexBuilder.get(book(listOf(text)), listOf("雅克·德·维特里")) { i ->
            if (i == 0) text else null
        }
        val entity = index.entities.firstOrNull { it.base == "雅克" }
        assertTrue(entity != null)
        assertEquals("Jacques de Vitry", entity!!.latin)
    }

    @Test
    fun bareBaseIsNotAnEntityWhenVariantsExist() {
        val text = "鲍德温三世与鲍德温四世，鲍德温是他们的统称。"
        val index = NameIndexBuilder.get(book(listOf(text)), listOf("鲍德温")) { i ->
            if (i == 0) text else null
        }
        assertTrue(index.entities.none { it.full == "鲍德温" })
        assertTrue(index.resolve("鲍德温") is NameResolution.Ambiguous)
    }

    @Test
    fun findsLatinRegnalVariants() {
        val text = "Baldwin III was king, and Baldwin IV was his nephew."
        val index = NameIndexBuilder.get(book(listOf(text)), listOf("Baldwin")) { i ->
            if (i == 0) text else null
        }
        val fulls = index.entities.map { it.full }.toSet()
        assertTrue("fulls=" + fulls, fulls.contains("Baldwin III"))
        assertTrue("fulls=" + fulls, fulls.contains("Baldwin IV"))
    }

    @Test
    fun keepsRegnalVariantsSeparate() {
        val text = "鲍德温三世与鲍德温四世是两位国王。"
        val index = NameIndexBuilder.get(book(listOf(text)), listOf("鲍德温")) { i ->
            if (i == 0) text else null
        }
        val fulls = index.entities.map { it.full }.toSet()
        assertTrue(fulls.contains("鲍德温三世"))
        assertTrue(fulls.contains("鲍德温四世"))
        assertTrue(index.resolve("鲍德温") is NameResolution.Ambiguous)
    }
}
