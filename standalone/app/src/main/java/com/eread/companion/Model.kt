package com.eread.companion

data class BookInfo(
    val bookUrl: String,
    val name: String,
    val author: String,
    val durChapterIndex: Int,
    val durChapterPos: Int,
    val durChapterTime: Long,
)

data class ChapterInfo(val index: Int, val title: String)

data class TraceHit(
    val chapterIndex: Int,
    val chapterTitle: String,
    val position: Int,
    val excerpt: String,
    val tail: String = "",
)

data class ScanResult(
    val query: String,
    val firstChapterIndex: Int,
    val firstChapterTitle: String,
    val hitCount: Int,
    val sampled: List<TraceHit>,
    val scanned: Int,
    val fetched: Int,
    val truncated: Boolean,
    val suggestion: String? = null,
    val anchorChapter: Int = -1,
    val aliasLong: String? = null,
    val aliasExcluded: Int = 0,
)

sealed interface UiState {
    object Idle : UiState
    data class Busy(val message: String) : UiState
    data class Done(
        val result: ScanResult,
        val summary: String?,
        val fromCache: Boolean,
        val note: String? = null,
        val mode: String = "最新",
        val canRegenerate: Boolean = false,
        val canSetLatest: Boolean = true,
    ) : UiState
    data class Error(val message: String, val suggestion: String? = null) : UiState
    data class Info(val message: String) : UiState
    data class Choose(val query: String, val candidates: List<NameEntity>) : UiState
}
