package com.eread.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** 人名索引落盘，避免每次启动重建。 */
object NameIndexStore {

    private val cache = HashMap<String, Pair<String, List<NameEntity>>>()

    private fun file(context: Context, book: LocalBook): File {
        val dir = File(context.filesDir, "name_index").apply { mkdirs() }
        val md = MessageDigest.getInstance("MD5").digest(book.fileName.toByteArray())
        val name = md.joinToString("") { "%02x".format(it) }
        return File(dir, name + ".json")
    }

    private fun version(book: LocalBook): String =
        book.fileName + ":" + book.chapters.size + ":" + (book.chapters.lastOrNull()?.end ?: 0L)

    fun load(context: Context, book: LocalBook): List<NameEntity> {
        val f = file(context, book)
        if (!f.exists()) return emptyList()
        val version = version(book)
        synchronized(cache) { cache[f.absolutePath]?.let { if (it.first == version) return it.second } }
        return runCatching {
            val o = JSONObject(f.readText())
            if (o.optString("v") != version(book)) return emptyList()
            val arr = o.optJSONArray("entities") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val e = arr.optJSONObject(i) ?: return@mapNotNull null
                NameEntity(
                    full = e.optString("full"),
                    base = e.optString("base"),
                    regnal = e.optString("regnal"),
                    segments = e.optJSONArray("seg")?.let { a ->
                        (0 until a.length()).map { a.optString(it) }
                    } ?: listOf(e.optString("base")),
                    latin = e.optString("latin"),
                    count = e.optInt("count", 0),
                    firstChapter = e.optInt("ch", 0),
                    firstPos = e.optInt("pos", 0),
                )
            }
        }.getOrDefault(emptyList()).also { entities ->
            synchronized(cache) { cache[f.absolutePath] = version to entities }
        }
    }

    fun save(context: Context, book: LocalBook, entities: List<NameEntity>) {
        if (entities.isEmpty()) return
        runCatching {
            val arr = JSONArray()
            entities.take(500).forEach { e ->
                arr.put(JSONObject()
                    .put("full", e.full)
                    .put("base", e.base)
                    .put("regnal", e.regnal)
                    .put("seg", JSONArray(e.segments))
                    .put("latin", e.latin)
                    .put("count", e.count)
                    .put("ch", e.firstChapter)
                    .put("pos", e.firstPos))
            }
            file(context, book).writeText(JSONObject().put("v", version(book)).put("entities", arr).toString())
            synchronized(cache) { cache[file(context, book).absolutePath] = version(book) to entities }
        }
    }
}
