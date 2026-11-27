package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NameParserTest {

    @Test
    fun parsesRegnalChinese() {
        val p = NameParser.parse("鲍德温三世")!!
        assertEquals("鲍德温", p.base)
        assertEquals("三世", p.regnal)
        assertEquals("鲍德温三世", p.full)
    }

    @Test
    fun parsesDotName() {
        val p = NameParser.parse("雷蒙·杜·皮伊")!!
        assertEquals("雷蒙", p.base)
        assertEquals(listOf("雷蒙", "杜", "皮伊"), p.segments)
        assertEquals("", p.regnal)
    }

    @Test
    fun parsesLatinWithRoman() {
        val p = NameParser.parse("Baldwin III")!!
        assertEquals("Baldwin", p.base)
        assertEquals("三世", p.regnal)
    }

    @Test
    fun normalizesDotVariants() {
        assertEquals("雷蒙·杜·皮伊", NameParser.normalize("雷蒙・杜•皮伊"))
    }

    @Test
    fun mergesBrokenLines() {
        assertEquals(listOf("雷蒙·杜·皮伊"), NameParser.mergeBrokenLines(listOf("雷蒙·", "杜·皮伊")))
        assertEquals(listOf("鲍德温三世"), NameParser.mergeBrokenLines(listOf("鲍德温", "三世")))
        assertEquals(listOf("第一行", "第二行"), NameParser.mergeBrokenLines(listOf("第一行", "第二行")))
    }

    @Test
    fun rejectsPlainWord() {
        assertEquals(null, NameParser.parse("他"))
    }
}
