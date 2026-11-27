package com.eread.companion

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UsageRecord(
    val feature: String,
    val prompt: Long,
    val completion: Long,
    val reasoning: Long,
    val at: Long,
    val book: String,
)

class UsageStore(context: Context) {

    private val sp = context.getSharedPreferences("usage", Context.MODE_PRIVATE)

    fun add(feature: String, prompt: Int, completion: Int, reasoning: Int, book: String) {
        val arr = runCatching { JSONArray(sp.getString("records", "[]")) }.getOrDefault(JSONArray())
        arr.put(JSONObject()
            .put("f", feature)
            .put("p", prompt)
            .put("c", completion)
            .put("r", reasoning)
            .put("t", System.currentTimeMillis())
            .put("b", book))
        val trimmed = if (arr.length() > 400) {
            JSONArray().also { out -> for (i in arr.length() - 400 until arr.length()) out.put(arr.getJSONObject(i)) }
        } else arr
        sp.edit().putString("records", trimmed.toString()).apply()
    }

    fun records(): List<UsageRecord> = runCatching {
        val arr = JSONArray(sp.getString("records", "[]"))
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            UsageRecord(o.optString("f"), o.optLong("p"), o.optLong("c"), o.optLong("r"), o.optLong("t"), o.optString("b"))
        }
    }.getOrDefault(emptyList())

    fun byFeature(): Map<String, LongArray> =
        records().groupBy { it.feature }.mapValues { (_, list) ->
            longArrayOf(
                list.sumOf { it.prompt },
                list.sumOf { it.completion },
                list.sumOf { it.reasoning },
            )
        }

    fun clear() {
        sp.edit().putString("records", "[]").apply()
    }
}
