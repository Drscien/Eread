package io.legado.app.ui.book.read.trace

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.LinearLayout
import io.legado.app.model.ReadBook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** 把检索、摘要、缓存、界面串起来 */
class TraceController(private val activity: Activity) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val settings = TraceSettings(activity)
    private val store = TraceStore(activity)
    private val llm = TraceLlm(settings)
    private val overlay = TraceOverlay(activity, ::onQuery, ::showSettings)
    private var job: Job? = null

    fun attach() {
        overlay.attach()
        overlay.render(TraceUiState.Idle)
    }

    fun hide() = overlay.hide()

    fun dispose() {
        job?.cancel()
        overlay.detach()
        scope.cancel()
    }

    private fun onQuery(name: String) {
        val book = ReadBook.book
        if (book == null) {
            overlay.render(TraceUiState.Failed("还没有打开书"))
            return
        }
        val chapterIndex = ReadBook.durChapterIndex
        val offset = book.durChapterPos

        job?.cancel()
        job = scope.launch {
            val cached = store.lookup(book.bookUrl, name, chapterIndex)
            if (cached != null) {
                overlay.render(
                    TraceUiState.Done(
                        result = TraceScanResult(
                            query = name,
                            firstChapterIndex = -1,
                            firstChapterTitle = cached.firstChapterTitle,
                            hitCount = cached.hitCount,
                            sampledHits = emptyList(),
                            scannedChapters = 0,
                            cachedChapters = 0,
                            truncated = false,
                            elapsedMs = 0,
                        ),
                        summary = cached.summary,
                        fromCache = true,
                    )
                )
                return@launch
            }

            overlay.render(TraceUiState.Scanning("正在从当前页往回找…"))
            val result = runCatching {
                TraceSearcher(book).scan(
                    query = name,
                    upToIndex = chapterIndex,
                    upToOffset = offset,
                    onProgress = { message ->
                        activity.runOnUiThread {
                            overlay.render(TraceUiState.Scanning(message))
                        }
                    },
                )
            }.getOrElse {
                overlay.render(TraceUiState.Failed("检索失败：" + (it.localizedMessage ?: "未知错误")))
                return@launch
            }

            if (result.hitCount == 0) {
                overlay.render(
                    TraceUiState.Failed(
                        "已回扫 " + result.scannedChapters + " 章，没找到「" + name +
                            "」。可能章节还没下载，或名字写法不同。"
                    )
                )
                return@launch
            }

            val config = settings.load()
            if (config.apiKey.isBlank()) {
                overlay.render(
                    TraceUiState.Done(
                        result = result,
                        summary = showFragments(result) + "\n\n（未配置 API Key，长按圆球可配置）",
                        fromCache = false,
                    )
                )
                return@launch
            }

            overlay.render(TraceUiState.Summarizing("找到 " + result.hitCount + " 处，正在总结…"))
            val summary = runCatching { llm.summarize(TracePrompt.build(result)) }
                .getOrElse { error ->
                    showFragments(result) + "\n\n（总结失败：" + (error.localizedMessage ?: "未知错误") + "）"
                }

            store.save(
                bookUrl = book.bookUrl,
                query = name,
                chapterIndex = chapterIndex,
                summary = summary,
                firstChapterTitle = result.firstChapterTitle,
                hitCount = result.hitCount,
            )
            overlay.render(TraceUiState.Done(result, summary, fromCache = false))
        }
    }

    private fun showFragments(result: TraceScanResult): String {
        val sb = StringBuilder()
        result.sampledHits.take(6).forEach { hit ->
            sb.append("【第 ").append(hit.chapterIndex + 1).append(" 章 ").append(hit.chapterTitle).append("】\n")
            sb.append(hit.excerpt).append("\n\n")
        }
        return sb.toString().trim()
    }

    private fun showSettings() {
        val config = settings.load()
        val padding = (16 * activity.resources.displayMetrics.density).toInt()
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding / 2, padding, 0)
        }
        val baseUrl = EditText(activity).apply {
            hint = "接口地址"
            setText(config.baseUrl)
        }
        val apiKey = EditText(activity).apply {
            hint = "API Key"
            setText(config.apiKey)
        }
        val model = EditText(activity).apply {
            hint = "模型名"
            setText(config.model)
        }
        container.addView(baseUrl)
        container.addView(apiKey)
        container.addView(model)

        AlertDialog.Builder(activity)
            .setTitle("人物速查设置")
            .setView(container)
            .setPositiveButton("保存") { _, _ ->
                settings.save(
                    TraceConfig(
                        baseUrl = baseUrl.text.toString(),
                        apiKey = apiKey.text.toString(),
                        model = model.text.toString(),
                    )
                )
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
