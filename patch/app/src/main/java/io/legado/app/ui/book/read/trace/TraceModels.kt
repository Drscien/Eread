package io.legado.app.ui.book.read.trace

/** 一次命中的片段 */
data class TraceHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val position: Int,
    val excerpt: String,
)

/** 回扫结果 */
data class TraceScanResult(
    val query: String,
    val firstChapterIndex: Int,
    val firstChapterTitle: String,
    val hitCount: Int,
    val sampledHits: List<TraceHit>,
    val scannedChapters: Int,
    val cachedChapters: Int,
    val truncated: Boolean,
    val elapsedMs: Long,
)

/** 悬浮窗状态 */
sealed interface TraceUiState {
    object Idle : TraceUiState
    data class Scanning(val message: String) : TraceUiState
    data class Summarizing(val message: String) : TraceUiState
    data class Done(
        val result: TraceScanResult,
        val summary: String,
        val fromCache: Boolean,
    ) : TraceUiState

    data class Failed(val message: String) : TraceUiState
}
