package com.eread.companion

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * 页码估计：
 * 1) 用户在阅读器看到页码后校准一次，记录 (章节, 字符位置, 页码)，反推每页字数；
 * 2) 同章有锚点时用最近锚点做线性外推/内插；
 * 3) 跨章用所有锚点的每页字数中位数；没有锚点才退回默认值。
 */
object PageEstimator {

    data class Anchor(val chapter: Int, val pos: Int, val page: Int, val cpp: Float)

    fun anchors(store: Store): List<Anchor> {
        return runCatching {
            val arr = JSONArray(store.anchorsJson)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val cpp = o.optDouble("cpp", 0.0).toFloat()
                if (cpp <= 0f) null
                else Anchor(o.optInt("c"), o.optInt("p"), o.optInt("page"), cpp)
            }
        }.getOrDefault(emptyList())
    }

    fun calibrate(store: Store, chapter: Int, pos: Int, page: Int): Float? {
        if (page <= 1 || pos <= 0) return null
        val cpp = pos.toFloat() / (page - 1)
        if (cpp < 80f || cpp > 3000f) return null
        val list = anchors(store).toMutableList()
        list.removeAll { it.chapter == chapter }
        list.add(Anchor(chapter, pos, page, cpp))
        save(store, list)
        store.charsPerPage = cpp.toInt()
        return cpp
    }

    fun estimate(store: Store, chapter: Int, pos: Int): Int {
        val all = anchors(store)
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
            val page = nearest.page + (pos - nearest.pos) / cpp
            return page.toInt().coerceAtLeast(1)
        }
        val cpp = all.map { it.cpp }.sorted().let {
            if (it.isEmpty()) store.charsPerPage.toFloat().coerceAtLeast(80f) else it[it.size / 2]
        }
        return (pos / cpp).toInt() + 1
    }

    private fun save(store: Store, list: List<Anchor>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().put("c", a.chapter).put("p", a.pos).put("page", a.page).put("cpp", a.cpp.toDouble()))
        }
        store.anchorsJson = arr.toString()
        if (list.isNotEmpty()) {
            store.charsPerPage = list.map { it.cpp }.sorted().let { it[it.size / 2] }.toInt()
        }
    }
}
