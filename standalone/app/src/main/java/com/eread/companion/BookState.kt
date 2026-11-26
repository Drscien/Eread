package com.eread.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class Card(
    val name: String,
    val summary: String,
    val pos: Int,
    val updatedAt: Long,
    val updates: Int,
    val importance: String = Importance.DEFAULT,
    val biography: String = "",
    val bioPos: Int = 0,
    val bioAt: Long = 0,
    val bioPrev: String = "",
)

object Importance {
    const val DEFAULT = "一般人物"
    val ALL = listOf("主要人物", "重要人物", "一般人物", "小人物")

    fun summaryLimit(level: String): Int = when (level) {
        "主要人物" -> 550
        "重要人物" -> 350
        "一般人物" -> 220
        else -> 120
    }

    fun bioLimit(level: String): Int = when (level) {
        "主要人物" -> 10000
        "重要人物" -> 5000
        "一般人物" -> 3000
        else -> 1000
    }
}

data class RereadEntry(val name: String, val bucket: Int, val summary: String, val at: Long)

class BookState private constructor(
    val bookUrl: String,
    var bookName: String,
) {
    var latestPos: Int = 0
    var currentPos: Int = 0
    val cards = LinkedHashMap<String, Card>()
    val rereads = ArrayList<RereadEntry>()

    fun toJson(): String {
        val cardsJson = JSONObject()
        cards.values.forEach { c ->
            cardsJson.put(c.name, JSONObject()
                .put("summary", c.summary)
                .put("pos", c.pos)
                .put("at", c.updatedAt)
                .put("updates", c.updates)
                .put("imp", c.importance)
                .put("bio", c.biography)
                .put("bioPos", c.bioPos)
                .put("bioAt", c.bioAt)
                .put("bioPrev", c.bioPrev))
        }
        val rereadJson = JSONArray()
        rereads.forEach { r ->
            rereadJson.put(JSONObject().put("name", r.name).put("bucket", r.bucket)
                .put("summary", r.summary).put("at", r.at))
        }
        return JSONObject()
            .put("bookUrl", bookUrl)
            .put("bookName", bookName)
            .put("latestPos", latestPos)
            .put("currentPos", currentPos)
            .put("cards", cardsJson)
            .put("rereads", rereadJson)
            .toString()
    }

    fun save(context: Context) {
        context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            .edit().putString(key(bookUrl), toJson()).apply()
    }

    fun resetAt(pos: Int) {
        latestPos = pos
        currentPos = pos
        cards.clear()
        rereads.clear()
    }

    companion object {
        fun load(context: Context, bookUrl: String, bookName: String): BookState {
            val state = BookState(bookUrl, bookName)
            val raw = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
                .getString(key(bookUrl), null) ?: return state
            runCatching {
                val o = JSONObject(raw)
                state.bookName = o.optString("bookName", bookName)
                state.latestPos = o.optInt("latestPos", 0)
                state.currentPos = o.optInt("currentPos", 0)
                val cards = o.optJSONObject("cards") ?: JSONObject()
                cards.keys().forEach { name ->
                    val c = cards.getJSONObject(name)
                    state.cards[name] = Card(
                        name = name,
                        summary = c.optString("summary"),
                        pos = c.optInt("pos", 0),
                        updatedAt = c.optLong("at", 0),
                        updates = c.optInt("updates", 0),
                        importance = c.optString("imp", Importance.DEFAULT),
                        biography = c.optString("bio"),
                        bioPos = c.optInt("bioPos", 0),
                        bioAt = c.optLong("bioAt", 0),
                        bioPrev = c.optString("bioPrev"),
                    )
                }
                val arr = o.optJSONArray("rereads") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    state.rereads.add(RereadEntry(r.optString("name"), r.optInt("bucket"),
                        r.optString("summary"), r.optLong("at")))
                }
            }
            return state
        }

        fun all(context: Context): List<BookState> {
            val sp = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            return sp.all.values.mapNotNull { raw ->
                runCatching { fromJson(context, raw as String) }.getOrNull()
            }
        }

        private fun fromJson(context: Context, raw: String): BookState? {
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val url = o.optString("bookUrl")
            if (url.isEmpty()) return null
            return load(context, url, o.optString("bookName"))
        }

        fun removeByName(context: Context, bookName: String) {
            val sp = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            val keys = sp.all.entries.filter { e ->
                runCatching { JSONObject(e.value as String).optString("bookName") == bookName }.getOrDefault(false)
            }.map { it.key }
            val editor = sp.edit()
            keys.forEach { editor.remove(it) }
            editor.apply()
        }

        fun key(bookUrl: String): String {
            val md = MessageDigest.getInstance("MD5").digest(bookUrl.toByteArray())
            return md.joinToString("") { "%02x".format(it) }
        }
    }
}

object Position {
    private const val CHAPTER_STRIDE = 100000

    fun global(chapter: Int, pos: Int): Int = chapter * CHAPTER_STRIDE + pos.coerceIn(0, CHAPTER_STRIDE - 1)

    fun chapterOf(global: Int): Int = global / CHAPTER_STRIDE

    fun posOf(global: Int): Int = global % CHAPTER_STRIDE

    /** 粗略字符距离：同章按字符，跨章按平均章节长度折算 */
    fun distance(fromGlobal: Int, toGlobal: Int, avgChapterChars: Int = 3000): Int {
        val dc = chapterOf(fromGlobal) - chapterOf(toGlobal)
        if (dc == 0) return kotlin.math.abs(fromGlobal - toGlobal)
        val samePart = kotlin.math.abs(posOf(fromGlobal) - posOf(toGlobal))
        return kotlin.math.abs(dc) * avgChapterChars + samePart
    }
}
