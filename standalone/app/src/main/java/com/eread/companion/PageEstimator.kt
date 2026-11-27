package com.eread.companion

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * 页码估计：按 (书, 来源) 隔离锚点，避免跨书/跨阅读器污染。
 */
object PageEstimator {

    data class Anchor(
        val book: String,
        val source: String,
        val chapter: Int,
        val pos: Int,
        val page: Int,
        val cpp: Float,
    )

    private val cache = HashMap<String, List<Anchor>>()

    fun anchors(store: Store, book: String, source: String): List<Anchor> {
        val cacheKey = book + "|" + source
        synchronized(cache) { cache[cacheKey]?.let { return it } }
        val loaded = parseAnchors(store, book, source)
        synchronized(cache) { cache[cacheKey] = loaded }
        return loaded
    }

    private fun parseAnchors(store: Store, book: String, source: String): List<Anchor> {
        return runCatching {
            val arr = JSONArray(store.anchorsJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val cpp = o.optDouble("cpp", 0.0).toFloat()
                if (cpp <= 0f) return@mapNotNull null
                val b = o.optString("b", "")
                val s = o.optString("src", "legado")
                if (b != book || s != source) return@mapNotNull null
                Anchor(b, s, o.optInt("c"), o.optInt("p"), o.optInt("page"), cpp)
            }
        }.getOrDefault(emptyList())
    }

    fun calibrate(store: Store, book: String, source: String, chapter: Int, pos: Int, page: Int): Float? {
        if (page <= 1 || pos <= 0) return null
        val cpp = pos.toFloat() / (page - 1)
        if (cpp < 80f || cpp > 3000f) return null

        val all = runCatching {
            val arr = JSONArray(store.anchorsJson)
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.takeIf { o ->
                    o.optString("b", "") == book && o.optString("src", "legado") == source
                }
            }
        }.getOrDefault(emptyList()).toMutableList()
        all.removeAll { it.optInt("c") == chapter }
        all.add(JSONObject().put("b", book).put("src", source).put("c", chapter).put("p", pos)
            .put("page", page).put("cpp", cpp.toDouble()))
        val arr = JSONArray()
        all.forEach { arr.put(it) }
        store.anchorsJson = arr.toString()
        synchronized(cache) { cache.remove(book + "|" + source) }

        if (source == "readest") store.cppReadest = cpp.toInt() else store.charsPerPage = cpp.toInt()
        return cpp
    }

    fun estimate(store: Store, book: String, source: String, chapter: Int, pos: Int): Int {
        val all = anchors(store, book, source)
        val same = all.filter { it.chapter == chapter }.sortedBy { it.pos }
        if (same.isNotEmpty()) {
            val nearest = same.minByOrNull { abs(it.pos - pos) }!!
            val cpp = if (same.size >= 2) {
                val first = same.first()
                val last = same.last()
                if (last.pos > first.pos && last.page > first.page) {
                    (last.pos - first.pos).toFloat() / (last.page - first.page)
                } else nearest.cpp
            } else nearest.cpp
            return (nearest.page + (pos - nearest.pos) / cpp).toInt().coerceAtLeast(1)
        }
        val cpp = if (all.isEmpty()) defaultCpp(store, source) else all.map { it.cpp }.sorted().let { it[it.size / 2] }
        return (pos / cpp).toInt() + 1
    }

    fun defaultCpp(store: Store, source: String): Float =
        (if (source == "readest" && store.cppReadest > 0) store.cppReadest else store.charsPerPage)
            .toFloat().coerceAtLeast(80f)

    private fun saveAll(store: Store, list: List<JSONObject>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        store.anchorsJson = arr.toString()
    }
}
