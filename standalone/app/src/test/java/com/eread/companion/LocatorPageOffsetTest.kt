package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归：定位必须返回原始章节下标，而不是去标点后的归一化下标。 */
class LocatorPageOffsetTest {

    private fun resource(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun returnsRawOffsetAndCountsPageHits() {
        val chapter = resource("chapter4.txt")
        val ocr = resource("ocr_page.txt")
        val book = LocalBook(
            "医院骑士团全史", "chapter4.txt",
            listOf(LocalChapter(0, "第二章 圣城守护者", 0L, chapter.length.toLong())),
        )
        val hit = Locator.locate(book, ocr, 0, null, textOf = { chapter })
        assertNotNull(hit)
        // 页首在原始文本约 25790 处；归一化下标约 23126
        assertTrue("pos=" + hit!!.pos, hit.pos in 25600..26000)

        var count = 0
        var i = chapter.indexOf("鲍德温五世")
        while (i >= 0) {
            if (i >= hit.pos) count++
            i = chapter.indexOf("鲍德温五世", i + 1)
        }
        assertEquals(2, count)
    }
}
