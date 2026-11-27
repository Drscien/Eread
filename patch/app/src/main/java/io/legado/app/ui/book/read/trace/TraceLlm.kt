package io.legado.app.ui.book.read.trace

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** OpenAI 兼容的 chat/completions 调用，不绑定具体厂商 */
class TraceLlm(private val settings: TraceSettings) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun summarize(prompt: String): String = withContext(Dispatchers.IO) {
        val config = settings.load()
        if (config.apiKey.isBlank()) error("未配置 API Key")

        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", prompt))

        val payload = JSONObject()
            .put("model", config.model)
            .put("temperature", 0.3)
            .put("max_tokens", 800)
            .put("messages", messages)

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer " + config.apiKey)
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("接口返回 " + response.code + "：" + text.take(160))
            }
            val content = JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
            content.trim().ifEmpty { error("模型返回为空") }
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val SYSTEM_PROMPT =
            "你是一个严谨的中文小说阅读助手，只根据用户提供的原文片段作答，不补充片段之外的信息。"
    }
}
