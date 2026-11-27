package com.eread.companion

/** OCR 近似名匹配：长度≥4 且编辑距离≤1 才认为是同一人的误读。 */
object NameMatcher {

    fun close(a: String, b: String, maxDistance: Int = 1): Boolean {
        if (a == b) return false
        if (a.length < 4 || b.length < 4) return false
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        return distance(a, b) <= maxDistance
    }

    fun nearest(name: String, entities: List<NameEntity>, maxDistance: Int = 1): NameEntity? =
        entities.filter {
            close(name, it.full, maxDistance) || (it.base.length >= 4 && close(name, it.base, maxDistance))
        }.maxByOrNull { it.count }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
