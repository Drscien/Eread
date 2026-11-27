package io.legado.app.ui.book.read.trace

import io.legado.app.data.entities.BookChapter

/**
 * 判断章节是否为"非正文"内容（序言、目录、感言等）。
 *
 * 目的：这些章节里可能出现人名，但并不是读者需要回忆的正文情节。
 * 只剔除明确的前置/附加内容，楔子、引子、序章这类通常含真实剧情的章节保留。
 */
object TraceFrontMatter {

    private val titleRegex = Regex(
        "^(序|序言|前言|引言|目录|内容简介|简介|作品相关|作者的话|上架感言|感言|" +
            "公告|通知|请假|说明|完本感言|新书感言|推荐票|月票|致读者|更新说明).{0,10}$"
    )

    fun isFrontMatter(chapter: BookChapter, contentLength: Int = Int.MAX_VALUE): Boolean {
        if (chapter.isVolume) return true
        val title = chapter.title.trim().replace(Regex("[\\s　]+"), "")
        if (title.isEmpty()) return true
        if (titleRegex.matches(title)) return true
        // 前 3 章里异常短的章节，多半是版权页/目录残片
        if (chapter.index <= 2 && contentLength < 600) return true
        return false
    }
}
