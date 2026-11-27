package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageNumbersTest {

    @Test
    fun readsBottomPageNumber() {
        val text = "正文内容第一行\n正文内容第二行\n54/102"
        assertEquals(54, PageNumbers.parse(text))
    }

    @Test
    fun ignoresYearLikeNumbersInBody() {
        val text = "1293/1294 年的战争\n正文\n正文"
        assertNull(PageNumbers.parse(text))
    }

    @Test
    fun readsChinesePage() {
        assertEquals(12, PageNumbers.parse("正文\n正文\n第 12 页"))
    }
}
