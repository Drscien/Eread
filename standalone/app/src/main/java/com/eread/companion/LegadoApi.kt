package com.eread.companion

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * legado 内置 Web 服务的只读接口，全部走 127.0.0.1。
 * 不依赖 legado 的任何内部类，因此它怎么更新都不影响本应用。
 */
class LegadoApi(private val port: Int) {

    fun books(): List<BookInfo> {
        val data = request("/getBookshelf")
        val arr = data as? JSONArray ?: return emptyList()
        val out = ArrayList<BookInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(
                BookInfo(
                    bookUrl = o.optString("bookUrl"),
                    name = o.optString("name"),
                    author = o.optString("author"),
                    durChapterIndex = o.optInt("durChapterIndex"),
                    durChapterPos = o.optInt("durChapterPos"),
                    durChapterTime = o.optLong("durChapterTime"),
                )
            )
        }
        return out
    }

    fun currentBook(): BookInfo? = books().maxByOrNull { it.durChapterTime }

    fun chapters(bookUrl: String): List<ChapterInfo> {
        val data = request("/getChapterList?url=" + enc(bookUrl))
        val arr = data as? JSONArray ?: return emptyList()
        val out = ArrayList<ChapterInfo>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            out.add(ChapterInfo(o.optInt("index", i), o.optString("title")))
        }
        return out.sortedBy { it.index }
    }

    fun content(bookUrl: String, index: Int): String? {
        val data = request("/getBookContent?url=" + enc(bookUrl) + "&index=" + index)
        return (data as? String)?.takeIf { it.isNotBlank() }
    }

    fun saveProgress(book: BookInfo, chapterIndex: Int, pos: Int, title: String) {
        val body = JSONObject()
            .put("name", book.name)
            .put("author", book.author)
            .put("durChapterIndex", chapterIndex)
            .put("durChapterPos", pos)
            .put("durChapterTitle", title)
            .put("durChapterTime", System.currentTimeMillis())
        post("/saveBookProgress", body.toString())
    }

    private fun post(path: String, json: String) {
        val url = URL("http://127.0.0.1:" + port + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 1500
        conn.readTimeout = 4000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        try {
            conn.outputStream.use { it.write(json.toByteArray()) }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun request(path: String): Any? {
        val url = URL("http://127.0.0.1:" + port + path)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 1200
        conn.readTimeout = 2500
        conn.requestMethod = "GET"
        try {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val ok = if (json.has("isSuccess")) json.optBoolean("isSuccess") else json.has("data")
            if (!ok) error(json.optString("errorMsg", "legado 返回失败"))
            return json.opt("data")
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
