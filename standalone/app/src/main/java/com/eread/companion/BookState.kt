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
    val relations: String = "",
    val relationsAt: Long = 0,
    val latin: String = "",
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
                .put("bioPrev", c.bioPrev)
                .put("rel", c.relations)
                .put("relAt", c.relationsAt)
                .put("latin", c.latin))
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
            .edit().putString(nameKey(bookName), toJson()).apply()
    }

    /** 合并同一本书在另一个来源下的档案。 */
    fun absorb(other: BookState) {
        if (other.latestPos > latestPos) latestPos = other.latestPos
        if (other.currentPos > currentPos) currentPos = other.currentPos
        other.cards.values.forEach { c ->
            val old = cards[c.name]
            if (old == null) {
                cards[c.name] = c
            } else {
                cards[c.name] = old.copy(
                    summary = if (c.summary.length > old.summary.length) c.summary else old.summary,
                    pos = maxOf(old.pos, c.pos),
                    updatedAt = maxOf(old.updatedAt, c.updatedAt),
                    updates = maxOf(old.updates, c.updates),
                    importance = if (old.importance == Importance.DEFAULT) c.importance else old.importance,
                    biography = if (c.biography.length > old.biography.length) c.biography else old.biography,
                    bioPos = maxOf(old.bioPos, c.bioPos),
                    bioAt = maxOf(old.bioAt, c.bioAt),
                    bioPrev = if (c.bioPrev.length > old.bioPrev.length) c.bioPrev else old.bioPrev,
                    relations = if (c.relations.length > old.relations.length) c.relations else old.relations,
                    relationsAt = maxOf(old.relationsAt, c.relationsAt),
                    latin = if (c.latin.isNotBlank()) c.latin else old.latin,
                )
            }
        }
        other.rereads.forEach { r ->
            if (rereads.none { it.name == r.name && it.bucket == r.bucket }) rereads.add(r)
        }
    }

    fun resetAt(pos: Int) {
        latestPos = pos
        currentPos = pos
        cards.clear()
        rereads.clear()
    }

    companion object {
        fun load(context: Context, bookUrl: String, bookName: String): BookState {
            val sp = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            sp.getString(nameKey(bookName), null)?.let { raw ->
                parse(raw, bookUrl, bookName)?.let { return it }
            }
            // 首次迁移：把同名（不同来源）的旧记录合并到书名键
            val merged = BookState(bookUrl, bookName)
            sp.all.values.forEach { raw ->
                val text = raw as? String ?: return@forEach
                val s = parse(text, bookUrl, bookName) ?: return@forEach
                if (norm(s.bookName) == norm(bookName)) merged.absorb(s)
            }
            if (merged.cards.isNotEmpty() || merged.latestPos > 0) merged.save(context)
            return merged
        }

        fun all(context: Context): List<BookState> {
            val sp = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            val byName = LinkedHashMap<String, BookState>()
            sp.all.values.forEach { raw ->
                val text = raw as? String ?: return@forEach
                val s = parse(text, "", "") ?: return@forEach
                if (s.bookName.isBlank()) return@forEach
                val k = norm(s.bookName)
                val cur = byName[k]
                if (cur == null) byName[k] = s else cur.absorb(s)
            }
            return byName.values.toList()
        }

        private fun parse(raw: String, fallbackUrl: String, fallbackName: String): BookState? {
            val o = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val url = o.optString("bookUrl", fallbackUrl)
            val name = o.optString("bookName", fallbackName)
            if (url.isEmpty() && name.isEmpty()) return null
            val state = BookState(url, name)
            state.latestPos = o.optInt("latestPos", 0)
            state.currentPos = o.optInt("currentPos", 0)
            val cards = o.optJSONObject("cards") ?: JSONObject()
            cards.keys().forEach { cardName ->
                val c = cards.getJSONObject(cardName)
                state.cards[cardName] = Card(
                    name = cardName,
                    summary = c.optString("summary"),
                    pos = c.optInt("pos", 0),
                    updatedAt = c.optLong("at", 0),
                    updates = c.optInt("updates", 0),
                    importance = c.optString("imp", Importance.DEFAULT),
                    biography = c.optString("bio"),
                    bioPos = c.optInt("bioPos", 0),
                    bioAt = c.optLong("bioAt", 0),
                    bioPrev = c.optString("bioPrev"),
                    relations = c.optString("rel"),
                    relationsAt = c.optLong("relAt", 0),
                    latin = c.optString("latin"),
                )
            }
            val arr = o.optJSONArray("rereads") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val r = arr.optJSONObject(i) ?: continue
                state.rereads.add(RereadEntry(r.optString("name"), r.optInt("bucket"),
                    r.optString("summary"), r.optLong("at")))
            }
            return state
        }

        private fun norm(s: String): String = s.trim().lowercase().replace(" ", "").replace("　", "")

        private fun nameKey(bookName: String): String = "t:" + key(norm(bookName))

        /** 一次性把按 bookUrl 存的旧档案合并到书名键，并删除旧键。 */
        fun migrate(context: Context) {
            val sp = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            if (sp.getBoolean("migrated_v1", false)) return
            val groups = LinkedHashMap<String, BookState>()
            val removeKeys = ArrayList<String>()
            sp.all.forEach { (k, v) ->
                val raw = v as? String ?: return@forEach
                val s = parse(raw, "", "") ?: return@forEach
                if (s.bookName.isBlank()) return@forEach
                val titleKey = norm(s.bookName)
                val cur = groups[titleKey]
                if (cur == null) groups[titleKey] = s else cur.absorb(s)
                if (k != nameKey(s.bookName)) removeKeys.add(k)
            }
            val editor = sp.edit()
            groups.values.forEach { editor.putString(nameKey(it.bookName), it.toJson()) }
            removeKeys.forEach { editor.remove(it) }
            editor.putBoolean("migrated_v1", true)
            editor.apply()
        }

        fun removeByName(context: Context, bookName: String) {
            val sp = context.getSharedPreferences("book_state", Context.MODE_PRIVATE)
            val keys = sp.all.entries.filter { e ->
                runCatching {
                    val o = JSONObject(e.value as String)
                    norm(o.optString("bookName")) == norm(bookName)
                }.getOrDefault(false)
            }.map { it.key }
            val editor = sp.edit()
            keys.forEach { editor.remove(it) }
            editor.remove(nameKey(bookName))
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
