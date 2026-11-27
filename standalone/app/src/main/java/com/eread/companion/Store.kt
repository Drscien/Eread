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
        get() {
            val raw = cfg.getString("api_key", "") ?: ""
            if (raw.isEmpty()) return ""
            return runCatching {
                if (Crypto.isEncrypted(raw)) {
                    Crypto.decrypt(raw)
                } else {
                    cfg.edit().putString("api_key", Crypto.encrypt(raw)).apply()
                    raw
                }
            }.getOrDefault(raw)
        }
        set(value) {
            val stored = if (value.isBlank()) "" else runCatching { Crypto.encrypt(value) }.getOrDefault(value)
            cfg.edit().putString("api_key", stored).apply()
        }

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

    fun recentSearches(book: String): List<String> {
        val root = runCatching { org.json.JSONObject(cfg.getString("recent_searches", "{}") ?: "{}") }
            .getOrDefault(org.json.JSONObject())
        val arr = root.optJSONArray(book) ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }
    }

    fun addRecentSearch(book: String, name: String) {
        if (book.isBlank() || name.isBlank()) return
        val root = runCatching { org.json.JSONObject(cfg.getString("recent_searches", "{}") ?: "{}") }
            .getOrDefault(org.json.JSONObject())
        val list = recentSearches(book).filter { it != name }.toMutableList()
        list.add(0, name)
        val arr = org.json.JSONArray()
        list.take(12).forEach { arr.put(it) }
        root.put(book, arr)
        cfg.edit().putString("recent_searches", root.toString()).apply()
    }

    var ocrNamesJson: String
        get() = cfg.getString("ocr_names", "{}") ?: "{}"
        set(value) = cfg.edit().putString("ocr_names", value).apply()

    fun ocrNames(book: String): List<String> {
        val arr = runCatching { org.json.JSONObject(ocrNamesJson).optJSONArray(book) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optString(it, "").takeIf { s -> s.isNotBlank() } }
    }

    fun setOcrNames(book: String, names: List<String>) {
        val root = runCatching { org.json.JSONObject(ocrNamesJson) }.getOrDefault(org.json.JSONObject())
        val arr = org.json.JSONArray()
        names.forEach { arr.put(it) }
        root.put(book, arr)
        ocrNamesJson = root.toString()
    }

    var cppReadest: Int
        get() = cfg.getInt("cpp_readest", 0)
        set(value) = cfg.edit().putInt("cpp_readest", value).apply()

    var readestPosJson: String
        get() = cfg.getString("readest_pos", "{}") ?: "{}"
        set(value) = cfg.edit().putString("readest_pos", value).apply()

    fun readestPos(book: String): Pair<Int, Int>? {
        val o = runCatching { org.json.JSONObject(readestPosJson).optJSONObject(book) }.getOrNull() ?: return null
        return o.optInt("c", 0) to o.optInt("p", 0)
    }

    fun setReadestPos(book: String, chapter: Int, pos: Int) {
        val root = runCatching { org.json.JSONObject(readestPosJson) }.getOrDefault(org.json.JSONObject())
        root.put(book, org.json.JSONObject().put("c", chapter).put("p", pos))
        readestPosJson = root.toString()
    }

    var aiNoticeShown: Boolean
        get() = cfg.getBoolean("ai_notice_shown", false)
        set(value) = cfg.edit().putBoolean("ai_notice_shown", value).apply()

    var captureMode: String
        get() = cfg.getString("capture_mode", "ocr") ?: "ocr"
        set(value) = cfg.edit().putString("capture_mode", value).apply()

    /** 用户自定义的 AI 文风与结构要求，会附加到所有概述/档案提示词。 */
    var stylePrompt: String
        get() = cfg.getString("style_prompt", "") ?: ""
        set(value) = cfg.edit().putString("style_prompt", value).apply()

    /** 回读分段密度：每多少全局字划分一个回读段。 */
    var rereadBucket: Int
        get() = cfg.getInt("reread_bucket", 2000).coerceIn(500, 20000)
        set(value) = cfg.edit().putInt("reread_bucket", value.coerceIn(500, 20000)).apply()

    /** 命中片段最多展示多少条。 */
    var hitSample: Int
        get() = cfg.getInt("hit_sample", 48).coerceIn(10, 200)
        set(value) = cfg.edit().putInt("hit_sample", value.coerceIn(10, 200)).apply()

    /** 人名最短长度。 */
    var nameMinLength: Int
        get() = cfg.getInt("name_min_length", 2).coerceIn(1, 6)
        set(value) = cfg.edit().putInt("name_min_length", value.coerceIn(1, 6)).apply()

    /** 人名最长长度。 */
    var nameMaxLength: Int
        get() = cfg.getInt("name_max_length", 12).coerceIn(4, 24)
        set(value) = cfg.edit().putInt("name_max_length", value.coerceIn(4, 24)).apply()

    /** OCR 近似名允许的最大编辑距离。 */
    var nameCloseDistance: Int
        get() = cfg.getInt("name_close_distance", 1).coerceIn(1, 2)
        set(value) = cfg.edit().putInt("name_close_distance", value.coerceIn(1, 2)).apply()

    /** 用户追加的身份词，逗号分隔，抽名时过滤。 */
    var nameStopWords: String
        get() = cfg.getString("name_stop_words", "") ?: ""
        set(value) = cfg.edit().putString("name_stop_words", value).apply()

    /** 概述温度，0–1。 */
    var summaryTemperature: Float
        get() = cfg.getFloat("summary_temperature", 0.35f).coerceIn(0f, 1f)
        set(value) = cfg.edit().putFloat("summary_temperature", value.coerceIn(0f, 1f)).apply()

    /** 概述最大 token。 */
    var summaryMaxTokens: Int
        get() = cfg.getInt("summary_max_tokens", 1600).coerceIn(400, 4000)
        set(value) = cfg.edit().putInt("summary_max_tokens", value.coerceIn(400, 4000)).apply()

    /** 关系/补档温度。 */
    var relationTemperature: Float
        get() = cfg.getFloat("relation_temperature", 0.4f).coerceIn(0f, 1f)
        set(value) = cfg.edit().putFloat("relation_temperature", value.coerceIn(0f, 1f)).apply()

    /** 每章最多统计多少次命中。 */
    var perChapterHits: Int
        get() = cfg.getInt("per_chapter_hits", 500).coerceIn(50, 2000)
        set(value) = cfg.edit().putInt("per_chapter_hits", value.coerceIn(50, 2000)).apply()

    /** 别名合并的最少重复次数。 */
    var aliasThreshold: Int
        get() = cfg.getInt("alias_threshold", 4).coerceIn(2, 20)
        set(value) = cfg.edit().putInt("alias_threshold", value.coerceIn(2, 20)).apply()

    /** 定位时前后各扩展多少个候选章。 */
    var candidateChapters: Int
        get() = cfg.getInt("candidate_chapters", 20).coerceIn(3, 60)
        set(value) = cfg.edit().putInt("candidate_chapters", value.coerceIn(3, 60)).apply()

    /** 补档时最多回看多少章。 */
    var evidenceChapters: Int
        get() = cfg.getInt("evidence_chapters", 30).coerceIn(5, 100)
        set(value) = cfg.edit().putInt("evidence_chapters", value.coerceIn(5, 100)).apply()

    /** 每位次人物最多保留几段共现片段。 */
    var cooccurSnippets: Int
        get() = cfg.getInt("cooccur_snippets", 2).coerceIn(1, 5)
        set(value) = cfg.edit().putInt("cooccur_snippets", value.coerceIn(1, 5)).apply()

    /** 关系共现的最大字距。 */
    var cooccurDistance: Int
        get() = cfg.getInt("cooccur_distance", 600).coerceIn(100, 2000)
        set(value) = cfg.edit().putInt("cooccur_distance", value.coerceIn(100, 2000)).apply()

    var ocrAiFix: Boolean
        get() = cfg.getBoolean("ocr_ai_fix", false)
        set(value) = cfg.edit().putBoolean("ocr_ai_fix", value).apply()

    var projectionAiFix: Boolean
        get() = cfg.getBoolean("projection_ai_fix", cfg.getBoolean("ocr_ai_fix", false))
        set(value) = cfg.edit().putBoolean("projection_ai_fix", value).apply()

    fun aiFixEnabled(): Boolean =
        if (captureMode == "projection") projectionAiFix else ocrAiFix

    var source: String
        get() = cfg.getString("source", "legado") ?: "legado"
        set(value) = cfg.edit().putString("source", value).apply()

    var manualBook: String
        get() = cfg.getString("manual_book", "") ?: ""
        set(value) = cfg.edit().putString("manual_book", value).apply()

    var manualChapter: Int
        get() = cfg.getInt("manual_chapter", 0)
        set(value) = cfg.edit().putInt("manual_chapter", value).apply()

    var manualPos: Int
        get() = cfg.getInt("manual_pos", 0)
        set(value) = cfg.edit().putInt("manual_pos", value).apply()

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
