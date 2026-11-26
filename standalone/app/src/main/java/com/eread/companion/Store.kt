package com.eread.companion

import android.content.Context
import org.json.JSONObject

class Store(context: Context) {

    private val cfg = context.getSharedPreferences("cfg", Context.MODE_PRIVATE)
    private val cache = context.getSharedPreferences("cache_v1", Context.MODE_PRIVATE)

    var legadoPort: Int
        get() = cfg.getInt("legado_port", 1122)
        set(value) = cfg.edit().putInt("legado_port", value).apply()

    var apiBase: String
        get() = cfg.getString("api_base", "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
        set(value) = cfg.edit().putString("api_base", value).apply()

    var apiKey: String
        get() = cfg.getString("api_key", "") ?: ""
        set(value) = cfg.edit().putString("api_key", value).apply()

    var ballX: Float
        get() = cfg.getFloat("ball_x", -1f)
        set(value) = cfg.edit().putFloat("ball_x", value).apply()

    var ballY: Float
        get() = cfg.getFloat("ball_y", -1f)
        set(value) = cfg.edit().putFloat("ball_y", value).apply()

    var reasoningLevel: String
        get() = cfg.getString("reasoning_level", "off") ?: "off"
        set(value) = cfg.edit().putString("reasoning_level", value).apply()

    var reasoningField: String
        get() = cfg.getString("reasoning_field", "reasoning_effort") ?: "reasoning_effort"
        set(value) = cfg.edit().putString("reasoning_field", value).apply()

    var priceInput: Float
        get() = cfg.getFloat("price_input", 1.5f)
        set(value) = cfg.edit().putFloat("price_input", value).apply()

    var priceOutput: Float
        get() = cfg.getFloat("price_output", 6f)
        set(value) = cfg.edit().putFloat("price_output", value).apply()

    var balanceUrl: String
        get() = cfg.getString("balance_url", "/user/balance") ?: "/user/balance"
        set(value) = cfg.edit().putString("balance_url", value).apply()

    var balanceMethod: String
        get() = cfg.getString("balance_method", "GET") ?: "GET"
        set(value) = cfg.edit().putString("balance_method", value).apply()

    var balancePath: String
        get() = cfg.getString("balance_path", "balance_infos[0].total_balance") ?: "balance_infos[0].total_balance"
        set(value) = cfg.edit().putString("balance_path", value).apply()

    var tab: Int
        get() = cfg.getInt("tab", 0)
        set(value) = cfg.edit().putInt("tab", value).apply()

    var helpShown: Boolean
        get() = cfg.getBoolean("help_shown", false)
        set(value) = cfg.edit().putBoolean("help_shown", value).apply()

    var theme: String
        get() = cfg.getString("theme", "dark") ?: "dark"
        set(value) = cfg.edit().putString("theme", value).apply()

    var aliasMerged: MutableSet<String>
        get() = (cfg.getStringSet("alias_merged", emptySet()) ?: emptySet()).toMutableSet()
        set(value) = cfg.edit().putStringSet("alias_merged", value).apply()

    var anchorsJson: String
        get() = cfg.getString("anchors", "[]") ?: "[]"
        set(value) = cfg.edit().putString("anchors", value).apply()

    var lastMode: String
        get() = cfg.getString("last_mode", "http") ?: "http"
        set(value) = cfg.edit().putString("last_mode", value).apply()

    var lastBook: String
        get() = cfg.getString("last_book", "") ?: ""
        set(value) = cfg.edit().putString("last_book", value).apply()

    var lastChapter: Int
        get() = cfg.getInt("last_chapter", -1)
        set(value) = cfg.edit().putInt("last_chapter", value).apply()

    var charsPerPage: Int
        get() = cfg.getInt("chars_per_page", 330)
        set(value) = cfg.edit().putInt("chars_per_page", value.coerceIn(80, 2000)).apply()

    var model: String
        get() = cfg.getString("model", "deepseek-flash") ?: "deepseek-flash"
        set(value) = cfg.edit().putString("model", value).apply()

    fun cached(bookUrl: String, query: String, chapter: Int): String? {
        val raw = cache.getString(bookUrl + "|" + query, null) ?: return null
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (System.currentTimeMillis() - json.optLong("at") > TTL) return null
        if (Math.abs(json.optInt("chapter", -1) - chapter) > 3) return null
        return json.optString("summary").takeIf { it.isNotBlank() }
    }

    fun putCache(bookUrl: String, query: String, chapter: Int, summary: String) {
        val json = JSONObject().put("summary", summary).put("chapter", chapter).put("at", System.currentTimeMillis())
        cache.edit().putString(bookUrl + "|" + query, json.toString()).apply()
    }

    companion object {
        private const val TTL = 7L * 24 * 60 * 60 * 1000
    }
}
