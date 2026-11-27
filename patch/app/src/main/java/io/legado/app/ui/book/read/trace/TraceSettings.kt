package io.legado.app.ui.book.read.trace

import android.content.Context

data class TraceConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
)

/** 只存本地：接口地址、Key、模型名。默认走 DeepSeek 的 OpenAI 兼容端点。 */
class TraceSettings(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("trace_settings", Context.MODE_PRIVATE)

    fun load(): TraceConfig = TraceConfig(
        baseUrl = sp.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL,
        apiKey = sp.getString("api_key", "") ?: "",
        model = sp.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL,
    )

    fun save(config: TraceConfig) {
        sp.edit()
            .putString("base_url", config.baseUrl.trim())
            .putString("api_key", config.apiKey.trim())
            .putString("model", config.model.trim())
            .apply()
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_MODEL = "deepseek-flash"
    }
}
