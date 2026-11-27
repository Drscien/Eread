package com.eread.companion

import org.json.JSONArray
import org.json.JSONObject

data class LocalChapter(val index: Int, val title: String, val start: Long, val end: Long)

data class LocalBook(
    val name: String,
    val fileName: String,
    val chapters: List<LocalChapter>,
) {
    fun toJson(): JSONObject {
        val arr = JSONArray()
        chapters.forEach { c ->
            arr.put(JSONObject().put("i", c.index).put("t", c.title).put("s", c.start).put("e", c.end))
        }
        return JSONObject().put("name", name).put("file", fileName).put("chapters", arr)
    }

    companion object {
        fun fromJson(o: JSONObject): LocalBook? {
            val name = o.optString("name")
            val file = o.optString("file")
            val arr = o.optJSONArray("chapters") ?: return null
            val list = ArrayList<LocalChapter>(arr.length())
            for (i in 0 until arr.length()) {
                val c = arr.optJSONObject(i) ?: continue
                list.add(LocalChapter(c.optInt("i", i), c.optString("t"), c.optLong("s"), c.optLong("e")))
            }
            if (name.isEmpty() || file.isEmpty() || list.isEmpty()) return null
            return LocalBook(name, file, list)
        }
    }
}
