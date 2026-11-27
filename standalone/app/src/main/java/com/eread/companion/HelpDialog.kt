package com.eread.companion

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** 设置中「帮助」：基本功能使用说明。 */
object HelpDialog {

    fun show(context: Context) {
        Theme.apply(context)
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(8))
        }

        fun heading(text: String) {
            box.addView(TextView(context).apply {
                this.text = text
                textSize = 13f
                setTextColor(Theme.accent)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(14), 0, dp(3))
            })
        }

        fun body(text: String) {
            box.addView(TextView(context).apply {
                this.text = text
                textSize = 12.5f
                setTextColor(Theme.textSoft)
                setLineSpacing(dp(3).toFloat(), 1f)
            })
        }

        body("Eread 是阅读辅助工具：查询人物、回溯出场、生成概述与关系，不修改 legado 与 Readest。")

        heading("1. 第一次使用")
        body(
            "① 设置 → 模型：填写接口地址与 API Key；\n" +
                "② 设置 → 悬浮窗：授予悬浮窗权限，选择截屏方式；\n" +
                "③ 运行页：选择 Legado 模式或 Readest 模式，点「启动悬浮窗」。"
        )

        heading("2. 两种读取方式")
        body(
            "Legado 模式：通过 legado 的 Web 服务读取书架与阅读位置，需在 legado 中开启 Web 服务。\n" +
                "Readest 模式：导入同一本 EPUB/TXT，用本地全文检索；位置来自截屏识别或手动选章。"
        )

        heading("3. 查询人物")
        body(
            "点悬浮球展开面板，输入不少于 2 个字的人名。\n" +
                "系统从当前阅读位置向前回溯，给出最早出场、命中片段与 AI 概述。\n" +
                "点击片段可跳转（Legado）或复制（Readest）。"
        )

        heading("4. 截屏识别")
        body(
            "阅读页点「截屏识别当前页」：截屏 → 识别文字 → 定位章节 → 校准页码 → 抽取本页人名。\n" +
                "OCR识别：需开启无障碍权限；\n" +
                "屏幕录制：需在设置中授权，状态栏会显示共享中；\n" +
                "未授权时点识别，会尝试拉起授权；被系统拦截时通过通知完成。"
        )

        heading("5. 页码校准")
        body(
            "· 手动校准：填入阅读器底部页码；\n" +
                "· 截屏校准：自动读页码并定位；\n" +
                "· 重建人名索引：扫描全书，建立人名实体，便于区分简称与全名。"
        )

        heading("6. 关系网")
        body(
            "选择一位主人物与若干次人物，点「生成关系列表」。\n" +
                "系统会扫描两人同段出现的原文，生成逐行关系，并自动把反向关系写入次人物档案。\n" +
                "「关系时间线」可查看已保存的关系清单。"
        )

        heading("7. 数据与档案")
        body(
            "数据页按书管理人物档案：最新概述、详细档案、人物关系。\n" +
                "长按条目可多选导出、分享或删除；同名书档案按书名合并。"
        )

        heading("8. 个性化")
        body(
            "设置 → 个性化 → 提示词：\n" +
                "可自定义 AI 文风、概述温度与长度、回读密度、命中上限、关系共现距离、名称降噪规则。\n" +
                "每项右侧问号有详细说明。"
        )

        heading("9. 常见问题")
        body(
            "识别不到文字：检查权限、画面亮度、是否受版权保护。\n" +
                "命中数偏少：确认已重新截屏识别，位置已更新到当前页。\n" +
                "人物档案重复：先重建人名索引，再打开数据页会自动合并近似名。"
        )

        val scroll = ScrollView(context).apply { addView(box) }
        AlertDialog.Builder(context)
            .setTitle("基本功能使用说明")
            .setView(scroll)
            .setPositiveButton("知道了", null)
            .show()
    }
}
