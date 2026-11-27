package com.eread.companion

/** 从截屏文本底部几行解析页码，避免把正文里的年份当成页码。 */
object PageNumbers {

    fun parse(text: String): Int? {
        val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        // 只看底部三行，且行内必须“以页码结尾”，避免正文年份误判
        for (line in lines.takeLast(3)) {
            if (line.length > 30) continue
            patterns(line)?.let { return it }
        }
        return null
    }

    private fun patterns(line: String): Int? {
        Regex("(\\d{1,4})\\s*[/／]\\s*(\\d{1,4})\\s*$").find(line)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return@let
            if (n in 1..5000) return n
        }
        Regex("第\\s*(\\d{1,4})\\s*页\\s*$").find(line)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return@let
            if (n in 1..5000) return n
        }
        Regex("(?i)page\\s*(\\d{1,4})\\s*$").find(line)?.let {
            val n = it.groupValues[1].toIntOrNull() ?: return@let
            if (n in 1..5000) return n
        }
        return null
    }
}
