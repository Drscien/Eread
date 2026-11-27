package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocatorTest {

    private fun book(count: Int) = LocalBook(
        "测试书",
        "test.txt",
        (0 until count).map { LocalChapter(it, "第" + (it + 1) + "节", 0L, 0L) },
    )

    @Test
    fun normalizeStripsPunctuation() {
        assertEquals("雅克德莫莱jacquesdemolay", Locator.normalize("雅克·德·莫莱（Jacques de Molay）"))
    }

    @Test
    fun locatesChapterDespiteOcrNoise() {
        val filler = "这是一段无关的过渡文字，用来把章节撑到足够长度。".repeat(6)
        val target = "1293年春，雅克德莫莱被选举为圣殿骑士团第二十三任大团长，他对自己悲剧性的命运一无所知。".repeat(3)
        val chapters = listOf(filler + filler, filler, target, filler)
        val provider: (Int) -> String? = { chapters.getOrNull(it) }
        val ocr = ("1293年春，雅克·德·莫莱被选举为圣殿骑士团第23任大团长，他对自己悲剧性的命运一无所知。").repeat(2)
        val hit = Locator.locate(book(chapters.size), ocr, 0, null, provider)
        assertNotNull(hit)
        assertEquals(2, hit!!.chapter)
    }
}
