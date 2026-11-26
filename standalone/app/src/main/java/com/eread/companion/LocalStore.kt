package com.eread.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile

/** 本地导入书的索引与正文读取 */
class LocalStore(context: Context) {

    private val dir = File(context.filesDir, "local").apply { mkdirs() }
    private val indexFile = File(dir, "index.json")

    fun books(): List<LocalBook> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { LocalBook.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun findMatching(title: String): LocalBook? {
        val key = normalize(title)
        if (key.isEmpty()) return null
        val all = books()
        return all.firstOrNull { normalize(it.name) == key }
            ?: all.firstOrNull { normalize(it.name).contains(key) || key.contains(normalize(it.name)) }
    }

    fun save(book: LocalBook) {
        val all = books().filterNot { it.fileName == book.fileName }.toMutableList()
        all.add(book)
        writeIndex(all)
    }

    fun remove(book: LocalBook) {
        runCatching { File(dir, book.fileName).delete() }
        writeIndex(books().filterNot { it.fileName == book.fileName })
    }

    fun chapterText(book: LocalBook, index: Int): String? {
        val chapter = book.chapters.getOrNull(index) ?: return null
        return runCatching {
            RandomAccessFile(File(dir, book.fileName), "r").use { raf ->
                val len = (chapter.end - chapter.start).toInt()
                if (len <= 0) return null
                val buf = ByteArray(len)
                raf.seek(chapter.start)
                raf.readFully(buf)
                String(buf, Charsets.UTF_8)
            }
        }.getOrNull()
    }

    private fun writeIndex(list: List<LocalBook>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        indexFile.writeText(arr.toString())
    }

    private fun normalize(s: String): String =
        s.replace(Regex("[\\s\\p{Punct}·：:（）()《》\\[\\]]"), "").lowercase()
}
