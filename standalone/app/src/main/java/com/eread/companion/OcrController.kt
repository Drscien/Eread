package com.eread.companion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 截屏、裁切、识别、定位、页码解析、校准。只做 OCR 相关的纯逻辑，不碰悬浮窗。 */
class OcrController(
    private val context: Context,
    private val store: Store,
    private val localStore: LocalStore,
) {

    data class Located(val hit: Locator.Hit?, val aiFixed: Boolean, val text: String)

    @Volatile
    var lastError: String? = null
        private set

    private var lastHash = 0
    private var lastText = ""

    fun enabled(): Boolean = OcrService.isEnabled(context)

    /** 当前模式是否可用；返回 null 表示可用。 */
    fun readyError(): String? {
        if (store.captureMode == "projection") {
            return if (ProjectionService.ready()) null else "录屏截屏未授权：设置 → 悬浮窗 → 录屏截屏授权"
        }
        if (Build.VERSION.SDK_INT < 30) return "Android 11 以下不支持无障碍截屏"
        return if (enabled()) null else "OCR识别权限未开启：设置 → 悬浮窗 → OCR识别权限"
    }

    suspend fun captureText(): String {
        lastError = null
        if (store.captureMode == "projection" && !ProjectionService.ready()) {
            requestProjectionPrompt()
            lastError = "请在通知栏点击「允许 Eread 录屏截屏」完成授权"
            return ""
        }
        readyError()?.let { lastError = it; return "" }
        val bitmap = captureBitmap() ?: return ""
        return recognize(bitmap)
    }

    /** 尝试直接拉起授权页；若系统拦截，则发一条可点击的通知。 */
    suspend fun requestProjectionPrompt() {
        if (ProjectionService.ready()) return
        val intent = Intent(context, ProjectionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (started) {
            repeat(6) {
                if (ProjectionService.ready()) {
                    ProjectionPrompter.cancel(context)
                    return
                }
                delay(200)
            }
        }
        ProjectionPrompter.prompt(context)
    }

    private suspend fun captureBitmap(): Bitmap? {
        val raw = when {
            store.captureMode == "projection" -> captureProjectionBitmap()
            else -> captureAccessibilityBitmap()
        }
        if (raw == null) {
            if (lastError == null) lastError = "没取到屏幕内容，靠近正文再试"
            return null
        }
        if (isMostlyBlack(raw)) {
            raw.recycle()
            lastError = "当前页面禁止截屏或画面过暗"
            return null
        }
        return raw
    }

    private suspend fun captureAccessibilityBitmap(): Bitmap? = withTimeoutOrNull(5_000) {
        suspendCancellableCoroutine<Bitmap?> { cont ->
            val svc = OcrService.instance
            if (svc == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            svc.capture { bmp ->
                if (cont.isActive) cont.resume(bmp) else bmp?.recycle()
            }
        }
    }

    private suspend fun captureProjectionBitmap(): Bitmap? = withTimeoutOrNull(4_000) {
        ProjectionService.capture()
    }

    private suspend fun recognize(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val body = runCatching { cropBody(bitmap) }.getOrNull()
        bitmap.recycle()
        if (body == null) {
            lastError = "截屏处理失败"
            return@withContext ""
        }
        val hash = quickHash(body)
        if (hash != 0 && hash == lastHash && lastText.isNotBlank()) {
            body.recycle()
            return@withContext lastText
        }
        var text = ""
        var attempt = 0
        while (attempt < 2 && text.length < 80) {
            if (attempt > 0) delay(300)
            text = withTimeoutOrNull(8_000) { Ocr.recognize(body) } ?: ""
            attempt++
        }
        body.recycle()
        if (text.isBlank()) {
            if (lastError == null) lastError = "没有识别到文字，靠近正文再试"
            return@withContext ""
        }
        lastHash = hash
        lastText = text
        text
    }

    /** 每 40 像素采样一次，估算整体亮度，识别纯黑的安全窗口。 */
    private fun isMostlyBlack(bitmap: Bitmap): Boolean {
        var sum = 0.0
        var count = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                sum += 0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF)
                count++
                x += 40
            }
            y += 40
        }
        return count > 0 && sum / count < 10.0
    }

    /** 8x8 灰度均值哈希，用于同一页去重。 */
    private fun quickHash(bitmap: Bitmap): Int {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        var hash = 0
        var avg = 0
        val pixels = IntArray(64)
        small.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        pixels.forEach { c ->
            avg += (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
        }
        avg /= 64
        pixels.forEachIndexed { i, c ->
            val v = (((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)) / 3
            if (v >= avg) hash = hash or (1 shl i)
        }
        small.recycle()
        return hash
    }

    suspend fun locate(local: LocalBook, text: String, hint: Int, allowAi: Boolean): Located {
        var hit = withContext(Dispatchers.IO) { Locator.locate(local, text, hint, localStore, candidateRange = store.candidateChapters) }
        var fixedByAi = false
        var finalText = text
        if ((hit == null || hit.score < 0.5) && allowAi && store.aiFixEnabled() && store.apiKey.isNotBlank()) {
            val fixed = aiFix(text)
            if (fixed.isNotBlank()) {
                // 无论定位是否成功，纠错后的文本都用于后续抽名
                finalText = fixed
                val second = withContext(Dispatchers.IO) { Locator.locate(local, fixed, hint, localStore) }
                if (second != null && (hit == null || second.score > hit!!.score)) {
                    hit = second
                    fixedByAi = true
                }
            }
        }
        return Located(hit, fixedByAi, finalText)
    }

    suspend fun aiFix(raw: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val prompt = "下面是一段 OCR 识别出来的小说正文，可能有个别错字、漏字或形近字错误。" +
                "请在不改变内容、不增删句子、不解释的前提下，只修正明显的识别错误，保持原文换行。" +
                "直接输出修正后的文字，不要加任何说明。\n\n" + raw.take(1500)
            val feature = if (store.captureMode == "projection") "录屏纠错" else "OCR纠错"
            Llm(store, UsageStore(context)).complete(prompt, feature, 1400).trim()
        }.getOrDefault("")
    }

    fun parsePageNumber(text: String): Int? = PageNumbers.parse(text)

    suspend fun calibrate(local: LocalBook, text: String, pageNo: Int, hint: Int): Float? {
        val hit = locate(local, text, hint, allowAi = store.aiFixEnabled()).hit ?: return null
        val cpp = PageEstimator.calibrate(store, local.name, "readest", hit.chapter, hit.pos, pageNo) ?: return null
        store.setReadestPos(local.name, hit.chapter, hit.pos)
        return cpp
    }

    private fun cropBody(src: Bitmap): Bitmap {
        val top = (src.height * 0.08f).toInt()
        val bottom = (src.height * 0.90f).toInt()
        val cropped = Bitmap.createBitmap(src, 0, top, src.width, (bottom - top).coerceAtLeast(10))
        val targetW = (cropped.width * 1.4f).toInt().coerceAtMost(1400)
        val targetH = (cropped.height * (targetW.toFloat() / cropped.width)).toInt()
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        if (scaled !== cropped) cropped.recycle()
        return scaled
    }
}
