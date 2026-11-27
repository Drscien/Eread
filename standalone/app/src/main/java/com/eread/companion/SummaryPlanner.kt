package com.eread.companion

/** 查询模式判定的纯逻辑，便于单测。 */
object SummaryPlanner {

    fun mode(
        force: Boolean,
        hasCard: Boolean,
        isReread: Boolean,
        anchorPos: Int,
        cardPos: Int,
    ): SummaryMode = when {
        force || !hasCard -> SummaryMode.FULL
        isReread -> SummaryMode.REREAD
        anchorPos > cardPos -> SummaryMode.INCREMENTAL
        else -> SummaryMode.FULL
    }
}
