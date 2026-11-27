package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameIndexLatinTest {

    private fun entity(full: String, latin: String = ""): NameEntity {
        val p = NameParser.parse(full)!!
        return NameEntity(p.full, p.base, p.regnal, p.segments, latin)
    }

    @Test
    fun latinQueryPrefersChineseEntity() {
        val index = NameIndex(
            listOf(
                entity("鲍德温三世", "Baldwin III"),
                entity("Baldwin IV"),
            )
        )
        val r = index.resolve("Baldwin III")
        assertTrue(r is NameResolution.Unique)
        assertEquals("鲍德温三世", (r as NameResolution.Unique).entity.full)
    }

    @Test
    fun bareLatinIsAmbiguous() {
        val index = NameIndex(
            listOf(
                entity("鲍德温三世", "Baldwin III"),
                entity("Baldwin IV"),
            )
        )
        val r = index.resolve("Baldwin")
        assertTrue(r is NameResolution.Ambiguous)
        val names = (r as NameResolution.Ambiguous).candidates.map { it.full }.toSet()
        assertTrue(names.toString(), names.contains("鲍德温三世"))
        assertTrue(names.toString(), names.contains("Baldwin IV"))
    }
}
