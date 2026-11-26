package com.eread.companion

import android.content.Context

/**
 * 全局配色。Activity / 悬浮窗创建时调用 apply(context) 决定明暗，
 * 切换主题后重建界面即可生效。
 */
object Theme {

    var isDark = true

    fun apply(context: Context) {
        isDark = Store(context).theme != "light"
    }

    val bg: Int get() = if (isDark) 0xFF11141A.toInt() else 0xFFF4F5F7.toInt()
    val bar: Int get() = if (isDark) 0xFF0E1116.toInt() else 0xFFFFFFFF.toInt()
    val card: Int get() = if (isDark) 0xFF191E26.toInt() else 0xFFFFFFFF.toInt()
    val cardSel: Int get() = if (isDark) 0xFF1E2635.toInt() else 0xFFE8EEFB.toInt()
    val cardAlt: Int get() = if (isDark) 0xFF232A35.toInt() else 0xFFEDEFF3.toInt()
    val field: Int get() = if (isDark) 0xFF2A2F3A.toInt() else 0xFFECEEF1.toInt()
    val panel: Int get() = if (isDark) 0xF21B1F27.toInt() else 0xF7FFFFFF.toInt()
    val chip: Int get() = if (isDark) 0xFF1B2740.toInt() else 0xFFE8EEFB.toInt()
    val divider: Int get() = if (isDark) 0xFF232A33.toInt() else 0xFFE3E6EB.toInt()

    val text: Int get() = if (isDark) 0xFFE8EAED.toInt() else 0xFF1B1F24.toInt()
    val textSoft: Int get() = if (isDark) 0xFFDDE1E6.toInt() else 0xFF33383F.toInt()
    val sub: Int get() = if (isDark) 0xFF8A9099.toInt() else 0xFF6B7280.toInt()
    val faint: Int get() = if (isDark) 0xFF6B7280.toInt() else 0xFF9AA0A6.toInt()

    val accent: Int get() = if (isDark) 0xFF7FB2FF.toInt() else 0xFF2F6FE0.toInt()
    val accentStrong: Int get() = if (isDark) 0xFF3B6FE0.toInt() else 0xFF2F6FE0.toInt()
    val onAccent: Int get() = 0xFFFFFFFF.toInt()
}
