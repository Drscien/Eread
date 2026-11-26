package com.eread.companion

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 首次启动与设置中「帮助」共用的说明弹窗。 */
object HelpDialog {

    fun show(context: Context) {
        Theme.apply(context)
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(6))
        }

        fun heading(text: String) {
            box.addView(TextView(context).apply {
                this.text = text
                textSize = 12.5f
                setTextColor(Theme.accent)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(4))
            })
        }

        fun body(text: String) {
            box.addView(TextView(context).apply {
                this.text = text
                textSize = 13f
                setTextColor(Theme.textSoft)
                setLineSpacing(dp(4).toFloat(), 1f)
            })
        }

        body(
            "Eread 是 legado 阅读器的独立伴生客户端，用于在阅读过程中检索人物并生成摘要。" +
                "它不修改 legado 的源码，仅通过其本地 Web 服务（默认 127.0.0.1:1122）读取书架、章节与阅读位置。"
        )

        heading("使用前")
        body(
            "1. 在 legado 中开启 Web 服务（我的 → Web 服务），记下端口。\n" +
                "2. 在 Eread 的设置中填写该端口、模型接口地址与 API Key。\n" +
                "3. 在运行页启动悬浮窗，并授予悬浮窗权限。"
        )

        heading("查询")
        body(
            "点击悬浮球展开面板，输入人物名称（不少于 2 字）后提交。系统自当前阅读位置向前扫描，定位该人物首次出现的位置，" +
                "并依据已读内容生成摘要，同时列出命中的原文片段。点击片段可跳转至 legado 对应位置。"
        )

        heading("三种摘要模式")
        body(
            "最新：该人物尚无档案时生成。\n" +
                "增量：当前位置晚于档案记录位置时，仅对新增区间生成并合并。\n" +
                "回读：当前位置早于档案记录位置时生成阶段性摘要，不改写既有最新档案。\n" +
                "同一位置重复查询直接使用缓存，不产生模型调用。"
        )

        heading("辅助功能")
        body(
            "快速识别：对当前页正文抽取人名，结果可直接用于检索。\n" +
                "页码校准：填入阅读器底部页码，改善页码估算。\n" +
                "数据页：本地书与接口书统一管理；长按条目可多选导出、分享或删除档案。\n" +
                "人物档案：在数据页进入单人档案，可生成传记式详细档案并按重要性设定篇幅上限。"
        )

        heading("限制")
        body(
            "页码为估算值，实际分页取决于 legado 的排版参数。\n" +
                "摘要与档案由模型生成，可能存在事实错误或遗漏，应以原文为准。\n" +
                "详细档案采用分段生成，篇幅较长的书籍耗时可达数分钟，并产生相应 token 开销。"
        )

        val scroll = ScrollView(context).apply {
            addView(box)
        }
        AlertDialog.Builder(context)
            .setTitle("帮助")
            .setView(scroll)
            .setPositiveButton("知道了", null)
            .show()
    }
}
