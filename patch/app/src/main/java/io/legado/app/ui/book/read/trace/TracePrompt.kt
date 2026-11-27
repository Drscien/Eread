package io.legado.app.ui.book.read.trace

/** 组织送模型的提示词，控制长度，强调"只依据已读片段" */
object TracePrompt {

    fun build(result: TraceScanResult): String {
        val sb = StringBuilder()
        sb.append("你是阅读辅助助手。下面是读者已经读过的部分里，与人物「")
        sb.append(result.query)
        sb.append("」相关的原文片段，按章节顺序排列，可能不完整。\n\n")

        result.sampledHits.forEach { hit ->
            sb.append("[第").append(hit.chapterIndex + 1).append("章 ")
            sb.append(hit.chapterTitle).append("] ")
            sb.append(hit.excerpt.take(180)).append("\n")
        }

        sb.append("\n请只依据以上片段，用 150~250 字中文概括：")
        sb.append("这个人是谁（身份、立场）、做过什么、与主角或其他人的关系。\n")
        sb.append("要求：\n")
        sb.append("1. 不要编造片段里没有的信息；片段不足时直接说明“已读部分信息不足”。\n")
        sb.append("2. 不要提及片段之外的剧情，也不要做剧情预测。\n")
        sb.append("3. 直接输出介绍，不要复述提示词，不要加标题。")
        return sb.toString()
    }
}
