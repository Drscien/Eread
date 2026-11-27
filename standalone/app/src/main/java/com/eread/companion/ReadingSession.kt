package com.eread.companion

import android.content.Context

/** 当前阅读上下文：绑定哪本本地书、哪一章、用哪个来源、每页字数。 */
class ReadingSession(
    private val context: Context,
    private val store: Store,
    private val localStore: LocalStore,
) {

    fun boundLocalBook(): LocalBook? {
        if (store.manualBook.isBlank()) return null
        return runCatching { localStore.findMatching(store.manualBook) }.getOrNull()
    }

    fun manualBookInfo(): BookInfo? {
        val local = boundLocalBook() ?: return null
        if (local.chapters.isEmpty()) return null
        val chapter = store.manualChapter.coerceIn(0, local.chapters.lastIndex)
        return BookInfo(
            bookUrl = "local://" + local.fileName,
            name = local.name,
            author = "",
            durChapterIndex = chapter,
            durChapterPos = store.manualPos.coerceAtLeast(0),
            durChapterTime = 0L,
        )
    }

    fun chapterTitleOf(book: LocalBook, index: Int): String {
        val raw = book.chapters.getOrNull(index)?.title?.trim().orEmpty()
        return if (raw.isNotEmpty()) raw.replace(Regex("[\\s　]+"), " ") else "第 " + (index + 1) + " 项"
    }

    fun currentBookKey(): String = if (store.source == "readest") store.manualBook else store.lastBook

    fun sourceKey(): String = if (store.source == "readest") "readest" else "legado"

    fun effectiveCpp(): Int =
        if (store.source == "readest" && store.cppReadest > 0) store.cppReadest else store.charsPerPage
}
