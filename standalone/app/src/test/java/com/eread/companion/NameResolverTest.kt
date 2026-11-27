package com.eread.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameResolverTest {

    private fun entity(full: String, base: String, regnal: String, count: Int): NameEntity =
        NameEntity(full, base, regnal, listOf(base), count = count)

    private val index = NameIndex(listOf(
        entity("鲍德温三世", "鲍德温", "三世", 30),
        entity("鲍德温四世", "鲍德温", "四世", 12),
        entity("雷蒙德三世", "雷蒙德", "三世", 23),
        NameEntity("雷蒙·杜·皮伊", "雷蒙", "", listOf("雷蒙", "杜", "皮伊"), count = 9),
    ))

    @Test
    fun exactRegnalIsUnique() {
        val r = index.resolve("鲍德温三世")
        assertTrue(r is NameResolution.Unique)
        assertEquals("鲍德温三世", (r as NameResolution.Unique).entity.full)
    }

    @Test
    fun baseWithMultipleRegnalIsAmbiguous() {
        val r = index.resolve("鲍德温")
        assertTrue(r is NameResolution.Ambiguous)
        assertEquals(2, (r as NameResolution.Ambiguous).candidates.size)
    }

    @Test
    fun shortNameAcrossDifferentNamesIsAmbiguous() {
        val r = index.resolve("雷蒙")
        assertTrue(r is NameResolution.Ambiguous)
        assertEquals(2, (r as NameResolution.Ambiguous).candidates.size)
    }

    @Test
    fun uniquePrefixAutoResolves() {
        val r = index.resolve("雷蒙德")
        assertTrue(r is NameResolution.Unique)
        assertTrue((r as NameResolution.Unique).auto)
    }

    @Test
    fun dotPrefixResolves() {
        val r = index.resolve("雷蒙·杜")
        assertTrue(r is NameResolution.Unique)
        assertEquals("雷蒙·杜·皮伊", (r as NameResolution.Unique).entity.full)
    }

    @Test
    fun unknownNameNotFound() {
        assertTrue(index.resolve("萨拉丁") is NameResolution.NotFound)
    }
}
