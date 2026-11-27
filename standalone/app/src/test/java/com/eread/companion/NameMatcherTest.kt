package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {

    private fun entity(full: String, count: Int): NameEntity {
        val p = NameParser.parse(full)!!
        return NameEntity(p.full, p.base, p.regnal, p.segments, count = count)
    }

    @Test
    fun mergesSingleOcrError() {
        assertTrue(NameMatcher.close("鲍穗温五世", "鲍德温五世"))
    }

    @Test
    fun doesNotMergeDifferentPeople() {
        assertFalse(NameMatcher.close("雷蒙德", "雷蒙德三世"))
    }

    @Test
    fun prefersMoreFrequentName() {
        val entities = listOf(entity("鲍穗温五世", 1), entity("鲍德温五世", 9))
        assertEquals("鲍德温五世", NameMatcher.nearest("鲍穗温五世", entities)?.full)
    }
}
