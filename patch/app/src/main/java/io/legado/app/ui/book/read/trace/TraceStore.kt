package io.legado.app.ui.book.read.trace

import android.content.Context
import org.json.JSONObject

data class CachedTrace(
    val summary: String,
    val firstChapterTitle: String,
    val hitCount: Int,
)

/** 摘要缓存：同一本书 + 同一个人名，阅读位置没大幅变化就不重复调用模型 */
class TraceStore(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("trace_cache_v1", Context.MODE_PRIVATE)

    fun lookup(bookUrl: String, query: String, chapterIndex: Int): CachedTrace? {
        val raw = sp.getString(key(bookUrl, query), null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val savedAt = json.optLong("at")
        val savedChapter = json.optInt("chapter", -1)
        if (System.currentTimeMillis() - savedAt > TTL_MS) return null
        if (savedChapter < 0 || Math.abs(savedChapter - chapterIndex) > 3) return null
        val summary = json.optString("summary")
        if (summary.isBlank()) return null
        return CachedTrace(
            summary = summary,
            firstChapterTitle = json.optString("firstTitle"),
            hitCount = json.optInt("hitCount"),
        )
    }

    fun save(
        bookUrl: String,
        query: String,
        chapterIndex: Int,
        summary: String,
        firstChapterTitle: String,
        hitCount: Int,
    ) {
        val json = JSONObject()
            .put("summary", summary)
            .put("firstTitle", firstChapterTitle)
            .put("hitCount", hitCount)
            .put("chapter", chapterIndex)
            .put("at", System.currentTimeMillis())
        sp.edit().putString(key(bookUrl, query), json.toString()).apply()
    }

    private fun key(bookUrl: String, query: String): String =
        bookUrl + "||" + query.trim().lowercase()

    companion object {
        private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
