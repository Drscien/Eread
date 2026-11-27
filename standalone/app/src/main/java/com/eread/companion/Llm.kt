package com.eread.companion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.exp
import kotlin.math.min

enum class SummaryMode { FULL, INCREMENTAL, REREAD }

class Llm(private val store: Store, private val usage: UsageStore) {

    private val systemPrompt: String
        get() = if (store.stylePrompt.isBlank()) SYSTEM else SYSTEM + "\n\n【用户自定义文风与结构要求】\n" + store.stylePrompt.trim()

    suspend fun summarize(
        result: ScanResult,
        mode: SummaryMode,
        previous: String? = null,
        feature: String = "概述",
    ): String = withContext(Dispatchers.IO) {
        if (store.apiKey.isBlank()) error("未配置 API Key")
        val prompt = when (mode) {
            SummaryMode.FULL -> fullPrompt(result)
            SummaryMode.INCREMENTAL -> incrementalPrompt(result, previous.orEmpty())
            SummaryMode.REREAD -> rereadPrompt(result, previous.orEmpty())
        }
        val body = JSONObject()
            .put("model", store.model)
            .put("temperature", store.summaryTemperature.toDouble())
            .put("max_tokens", store.summaryMaxTokens)
        if (store.reasoningLevel != "off") {
            body.put(store.reasoningField, store.reasoningLevel)
        } else {
            body.put("thinking", JSONObject().put("type", "disabled"))
        }
        body.put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", systemPrompt))
                .put(JSONObject().put("role", "user").put("content", prompt)))

        val json = postJson(body)
        recordUsage(json, feature)
        json.getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content").trim().ifEmpty { error("模型返回为空") }
    }

    suspend fun complete(
        prompt: String,
        feature: String,
        maxTokens: Int = 1200,
        applyStyle: Boolean = true,
    ): String =
        withContext(Dispatchers.IO) {
            if (store.apiKey.isBlank()) error("未配置 API Key")
            val system = if (applyStyle) systemPrompt else SYSTEM
            val body = JSONObject()
                .put("model", store.model)
                .put("temperature", if (feature.startsWith("关系") || feature == "人物补档") store.relationTemperature.toDouble() else 0.4)
                .put("max_tokens", maxTokens)
            if (store.reasoningLevel != "off") {
                body.put(store.reasoningField, store.reasoningLevel)
            } else {
                body.put("thinking", JSONObject().put("type", "disabled"))
            }
            body.put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", SYSTEM))
                .put(JSONObject().put("role", "user").put("content", prompt)))
            val json = postJson(body)
            recordUsage(json, feature)
            json.getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").optString("content").trim()
        }

    private fun postJson(body: JSONObject): JSONObject {
        var attempt = 0
        var last: Exception? = null
        while (attempt < 3) {
            attempt++
            try {
                var response = rawPost(body)
                if (response.first == 400 && body.has("thinking")) {
                    body.remove("thinking")
                    response = rawPost(body)
                }
                if (response.first in 200..299) return JSONObject(response.second)
                if (response.first == 429 || response.first in 500..599) {
                    last = IllegalStateException("接口 " + response.first + "：" + response.second.take(160))
                    if (attempt < 3) {
                        Thread.sleep(if (attempt == 1) 1000L else 3000L)
                        continue
                    }
                    throw last
                }
                error("接口 " + response.first + "：" + response.second.take(160))
            } catch (e: Exception) {
                if (e is java.io.IOException && attempt < 3) {
                    last = e
                    Thread.sleep(if (attempt == 1) 1000L else 3000L)
                    continue
                }
                throw e
            }
        }
        throw last ?: IllegalStateException("请求失败")
    }

    private fun rawPost(body: JSONObject): Pair<Int, String> {
        val url = URL(store.apiBase.trimEnd('/') + "/chat/completions")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 180000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Authorization", "Bearer " + store.apiKey)
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            code to text
        } finally {
            conn.disconnect()
        }
    }

    private fun recordUsage(json: JSONObject, feature: String) {
        val u = json.optJSONObject("usage") ?: return
        val p = u.optInt("prompt_tokens", 0)
        val c = u.optInt("completion_tokens", 0)
        val r = u.optJSONObject("completion_tokens_details")?.optInt("reasoning_tokens", 0) ?: 0
        usage.add(feature, p, c, r, "")
    }

    private fun fullPrompt(r: ScanResult): String {
        val anchor = if (r.anchorChapter >= 0) r.anchorChapter else r.firstChapterIndex.coerceAtLeast(0)
        val span = (anchor - r.firstChapterIndex).coerceAtLeast(1)
        val recentFrom = anchor - maxOf(3, span / 5)

        val recent = r.sampled.filter { it.chapterIndex >= recentFrom }
        val early = r.sampled.filter { it.chapterIndex < recentFrom }
        val target = (220 + min(r.hitCount, 4000) / 15).coerceIn(220, 600)

        val sb = StringBuilder()
        sb.append("读者正在读第 ").append(anchor + 1).append(" 项，前面的内容都已读完。")
        sb.append("下面是已读部分中与「").append(r.query).append("」相关的原文片段。\n\n")
        appendBlock(sb, "【近期片段】（读者刚读过，描述主体）", recent, 180)
        appendBlock(sb, "【早期片段】（只交代身份与来历，简要带过）", early, 150)
        sb.append("请用约 ").append(target).append(" 字，分三段写：\n")
        sb.append("① 身份与立场；② 近期经历与当前处境（以近期片段为主，写具体事件）；")
        sb.append("③ 与主要人物的关系。\n")
        sb.append("早期内容一笔带过；不要编造；信息不足要说明；不要预测后续剧情；直接输出正文。")
        return sb.toString()
    }

    private fun incrementalPrompt(r: ScanResult, previous: String): String {
        val anchor = if (r.anchorChapter >= 0) r.anchorChapter else 0
        val recent = r.sampled.sortedByDescending { it.chapterIndex }.take(14)
        val sb = StringBuilder()
        sb.append("这是「").append(r.query).append("」的既有档案（截至上次阅读位置）：\n")
        sb.append(previous.trim()).append("\n\n")
        sb.append("读者又往后读到了第 ").append(anchor + 1).append(" 项，新出现的片段如下：\n")
        recent.forEach { h ->
            sb.append("- [第").append(h.chapterIndex + 1).append("项] ")
            sb.append(h.excerpt.take(160)).append("\n")
        }
        sb.append("\n请把新信息合并进档案，输出一份更新后的完整档案：\n")
        sb.append("保持原有【身份/立场/关系】中仍然成立的部分，补充或修正【近期经历】；")
        sb.append("总长度比上一版略增，但不超过 600 字；不要丢掉旧档案里仍然有效的信息；")
        sb.append("不要编造；直接输出正文，不要标题。")
        return sb.toString()
    }

    private fun rereadPrompt(r: ScanResult, latest: String): String {
        val anchor = if (r.anchorChapter >= 0) r.anchorChapter else 0
        val weighted = r.sampled
            .map { it to weight(it, r) }
            .filter { it.second > 0.05 }
            .sortedByDescending { it.second }
            .take(12)
            .map { it.first }

        val sb = StringBuilder()
        sb.append("读者正在回读第 ").append(anchor + 1).append(" 项附近的内容。\n\n")
        if (latest.isNotBlank()) {
            sb.append("【人物当前档案】（读者读到的最新状态，不要与之矛盾）\n")
            sb.append(latest.trim()).append("\n\n")
        }
        sb.append("【回读位置的片段】（离当前页越近越重要）\n")
        weighted.forEach { h ->
            val d = Position.distance(Position.global(anchor, 0), Position.global(h.chapterIndex, 0))
            val tag = if (d < 60000) "近" else "远"
            sb.append("- [").append(tag).append("·第").append(h.chapterIndex + 1).append("项] ")
            sb.append(h.excerpt.take(150)).append("\n")
        }
        sb.append("\n请用约 200–350 字，说明这个人物在读者回读的这段内容里的处境与行为：\n")
        sb.append("以【近】的片段为主，【远】的一笔带过；档案只作背景，不要重复复述；")
        sb.append("不要与档案矛盾；不要编造；直接输出正文。")
        return sb.toString()
    }

    /** 正态权重：距当前页越远权重越低，150k 字以外为 0 */
    private fun weight(hit: TraceHit, r: ScanResult): Double {
        val anchor = if (r.anchorChapter >= 0) r.anchorChapter else 0
        val d = Position.distance(
            Position.global(anchor, 0),
            Position.global(hit.chapterIndex, 0)
        )
        if (d > 150000) return 0.0
        val z = d / 30000.0
        return exp(-0.5 * z * z)
    }

    private fun appendBlock(sb: StringBuilder, title: String, hits: List<TraceHit>, take: Int) {
        if (hits.isEmpty()) return
        sb.append(title).append("\n")
        hits.forEach { h ->
            sb.append("- [第").append(h.chapterIndex + 1).append("项] ")
            sb.append(h.excerpt.take(take)).append("\n")
        }
        sb.append("\n")
    }

    companion object {
        private const val SYSTEM = "你是一个严谨的中文小说阅读助手，只根据用户提供的原文片段作答，能区分近期与早期信息的主次。"
    }
}
