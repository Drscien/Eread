package com.eread.companion

/** 人名解析：世次、· 结构、拉丁对照。只做结构判断，不判断身份。 */
object NameParser {

    private val roleTails = setOf("兄弟", "儿子", "父亲", "母亲", "妻子", "女儿", "侄子", "部将", "将领", "国王", "苏丹", "教皇")

    /** 去掉「萨拉丁的兄弟阿迪勒」这类描述性前缀，只保留人名。 */
    fun cleanDescriptor(raw: String): String {
        var s = raw.trim().trim('，', '。', '、', '「', '」', '（', '）', '(', ')')
        if (s.contains('的')) {
            val tail = s.substringAfterLast('的').trim()
            if (tail.length >= 2 && tail !in roleTails && tail.any { it.code in 0x4e00..0x9fff }) {
                s = tail
            }
        }
        return s
    }

    private val dotChars = charArrayOf('•', '・', '‧', '．', '.', '。')
    private val cnNum = mapOf(
        "一" to 1, "二" to 2, "三" to 3, "四" to 4, "五" to 5,
        "六" to 6, "七" to 7, "八" to 8, "九" to 9, "十" to 10,
        "十一" to 11, "十二" to 12,
    )
    private val roman = mapOf(
        "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5,
        "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10,
    )
    private val cnOfNum = cnNum.entries.associate { (k, v) -> v to k }

    data class Parsed(
        val full: String,
        val base: String,
        val regnal: String,
        val segments: List<String>,
        val latin: String = "",
    ) {
        val displayName: String get() = full
    }

    fun normalize(raw: String): String {
        var s = raw.trim()
        dotChars.forEach { s = s.replace(it, '·') }
        s = s.replace(Regex("[\\s　]+"), "·")
        s = s.replace(Regex("·+"), "·")
        return s.trim('·', ' ')
    }

    fun parseLatin(raw: String): Parsed? {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (!Regex("^[A-Za-z][A-Za-z .'\\-]+$").matches(s)) return null
        val m = Regex("^(.*?)\\s+(VIII|VII|VI|IV|IX|III|II|X|V|I)$").find(s)
        var base = s
        var regnal = ""
        if (m != null) {
            base = m.groupValues[1].trim()
            regnal = cnNumOf(roman[m.groupValues[2]] ?: 0)
        }
        return Parsed(full = s, base = base, regnal = regnal, segments = listOf(base), latin = s)
    }

    fun parse(raw: String): Parsed? {
        parseLatin(raw)?.let { return it }
        val s = normalize(raw)
        if (s.length < 2) return null
        var body = s
        var regnal = ""

        // 中文世次：鲍德温三世 / 鲍德温十二世
        Regex("^(.*?)(十[一二]?|[一二三四五六七八九十])(世)$").find(body)?.let {
            body = it.groupValues[1].trim('·')
            regnal = it.groupValues[2] + "世"
        }
        // 阿拉伯数字世次：鲍德温3世
        if (regnal.isEmpty()) {
            Regex("^(.*?)(\\d{1,2})(世)$").find(body)?.let {
                val n = it.groupValues[2].toIntOrNull() ?: 0
                if (n in 1..20) {
                    body = it.groupValues[1].trim('·')
                    regnal = cnNumOf(n)
                }
            }
        }
        // 拉丁罗马数字世次：Baldwin III 的形式已在 parseLatin 处理
        if (body.length < 2) return null

        val segments = body.split('·').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null
        val base = segments.first()
        val full = if (regnal.isEmpty()) body else body + regnal
        return Parsed(full = full, base = base, regnal = regnal, segments = segments)
    }

    private fun cnNumOf(n: Int): String {
        if (n <= 0) return ""
        return (cnOfNum[n] ?: n.toString()) + "世"
    }

    /** 合并被换行打断的名字：行尾 · 或下一行以 · 开头。 */
    fun mergeBrokenLines(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        var buffer = ""
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (buffer.isEmpty()) {
                buffer = line
            } else {
                val regnalContinuation = Regex("^(十[一二]?|[一二三四五六七八九十])世$").matches(line) &&
                    Regex("[\\u4e00-\\u9fff]{2,6}$").containsMatchIn(buffer)
                val dotContinuation = line.startsWith("·") || buffer.endsWith("·")
                buffer = if (regnalContinuation || dotContinuation) {
                    if (regnalContinuation && !buffer.endsWith("·")) buffer + line else buffer + line
                } else {
                    out.add(buffer)
                    line
                }
            }
        }
        if (buffer.isNotEmpty()) out.add(buffer.trim('·'))
        return out.map { it.trim('·') }
    }
}
