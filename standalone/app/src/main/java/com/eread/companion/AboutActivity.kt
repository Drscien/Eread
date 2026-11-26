package com.eread.companion

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AboutActivity : Activity() {

    private val d get() = resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Theme.apply(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Theme.bg)
            setPadding(dp(20), dp(24), dp(20), dp(16))
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(TextView(this).apply {
            text = "关于"
            textSize = 24f
            setTextColor(Theme.text)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "返回"
            textSize = 13f
            setTextColor(Theme.accent)
            setPadding(dp(12), dp(6), dp(4), dp(6))
            setOnClickListener { finish() }
        })
        root.addView(header)

        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "0.4.0"

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.card, dp(14))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        card.addView(TextView(this).apply {
            text = "Eread · 人物速查"
            textSize = 17f
            setTextColor(Theme.text)
        })
        card.addView(TextView(this).apply {
            text = "版本 " + version + "　·　不修改 legado 的独立伴生工具"
            textSize = 11.5f
            setTextColor(Theme.sub)
            setPadding(0, dp(6), 0, 0)
        })
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.topMargin = dp(16)
        root.addView(card, clp)

        val entries = listOf(
            "开发人员" to { showDeveloper() },
            "更新日志" to { showChangelog() },
            "崩溃日志" to { showCrashLog() },
            "保存日志" to { saveLog() },
            "用户隐私与协议" to { showPrivacy() },
            "开源许可与免责声明" to { showLicenses() },
        )
        val listCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Theme.card, dp(14))
            setPadding(0, dp(4), 0, dp(4))
        }
        entries.forEachIndexed { i, (title, action) ->
            if (i > 0) {
                listCard.addView(android.view.View(this).apply { setBackgroundColor(Theme.divider) },
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { leftMargin = dp(16) })
            }
            listCard.addView(row(title) { action() })
        }
        val llp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        llp.topMargin = dp(14)
        root.addView(listCard, llp)

        root.addView(TextView(this).apply {
            text = "Eread 与 legado 无关，仅通过其公开的 Web 服务读取书籍与进度。"
            textSize = 10.5f
            setTextColor(Theme.faint)
            setPadding(dp(2), dp(16), dp(2), 0)
        })

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(root)
        })
    }

    private fun row(title: String, onClick: () -> Unit): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { onClick() }
            addView(TextView(this@AboutActivity).apply {
                text = title
                textSize = 13.5f
                setTextColor(Theme.text)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(this@AboutActivity).apply {
                text = "›"
                textSize = 18f
                setTextColor(Theme.faint)
            })
        }

    private fun dialog(title: String, message: String) {
        val scroll = ScrollView(this)
        scroll.addView(TextView(this).apply {
            text = message
            textSize = 13f
            setTextColor(Theme.textSoft)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(20), dp(10), dp(20), dp(6))
        })
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showDeveloper() = dialog(
        "开发人员",
        "Eread 由个人独立开发与维护。\n\n" +
            "设计与开发：Venus（启明星）\n" +
            "联系 QQ：3140661756\n" +
            "问题反馈：https://github.com/Drscien/Eread/issues\n\n" +
            "阅读一些西方题材或者与之相关的小说或者论述文本时，记住那些繁杂的名字对很多人来说似乎都是难事，因此在 ai 辅助下开发了这个应用，帮助使用 legado 的读者快速查询人物。"
    )

    private fun showChangelog() = dialog(
        "更新日志",
        "0.1.0\n" +
            "· 悬浮球查询：回溯人物最早出场，最新 / 增量 / 回读三种概述\n" +
            "· 原文片段高亮，点击跳转 legado 对应位置\n" +
            "· 快速识别：AI 抓取当前页人名，点击直达检索\n" +
            "· 人物档案：最新概述 + 传记式详细档案（四档重要性）\n" +
            "· 数据页：本地书与接口书统一管理，多选导出 zip 与系统分享\n" +
            "· 本地导入 TXT / EPUB，页码估算与校准\n" +
            "· Token 消耗统计、账户余额查询、深色 / 浅色主题"
    )

    private fun crashFile(): File = File(filesDir, "crash.log")

    private fun showCrashLog() {
        val f = crashFile()
        val text = if (f.exists() && f.length() > 0) f.readText() else "暂无崩溃记录。"
        dialog("崩溃日志", if (text.length > 20000) text.takeLast(20000) else text)
    }

    private fun saveLog() {
        val f = crashFile()
        if (!f.exists() || f.length() == 0L) {
            Toast.makeText(this, "暂无日志可保存", Toast.LENGTH_SHORT).show()
            return
        }
        val name = "eread-log-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()) + ".txt"
        val ok = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Eread")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
                contentResolver.openOutputStream(uri)!!.use { it.write(f.readBytes()) }
            } else {
                val dir = File(getExternalFilesDir(null), "Eread")
                dir.mkdirs()
                File(dir, name).writeBytes(f.readBytes())
            }
            true
        }.getOrDefault(false)
        Toast.makeText(
            this,
            if (ok) "日志已保存到 Download/Eread" else "保存失败",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showPrivacy() = dialog(
        "用户隐私与协议",
        "1. 数据存放\n" +
            "书籍文件、人物档案、位置与缓存全部保存在本机应用私有目录。Eread 没有自己的服务器，也不会自动上传任何数据。\n\n" +
            "2. AI 请求\n" +
            "只有在你主动查询、识别或生成档案时，相关原文片段才会发送到你在设置中填写的模型接口（默认 DeepSeek）。发送内容仅限当次需要的片段，不包含整本书。\n\n" +
            "3. legado 数据\n" +
            "Eread 通过 127.0.0.1 上的 legado Web 服务读取书架、章节与阅读进度，不修改 legado 的任何数据。\n\n" +
            "4. 权限\n" +
            "悬浮窗权限用于显示悬浮球与查询面板；存储访问仅用于导出档案与日志。\n\n" +
            "5. 你的控制权\n" +
            "可随时在数据页删除某本书的全部档案与缓存，或清空 Token 统计。\n\n" +
            "使用本应用即表示你同意上述说明。"
    )

    private fun showLicenses() = dialog(
        "开源许可与免责声明",
        "本应用使用以下开源组件：\n" +
            "· Kotlin（Apache License 2.0）\n" +
            "· Kotlin Coroutines（Apache License 2.0）\n" +
            "· Android SDK / Jetpack（Apache License 2.0）\n\n" +
            "legado 是独立开源项目，Eread 不包含其代码，仅通过其公开 HTTP 接口通信。\n\n" +
            "免责声明：\n" +
            "本软件按「现状」提供，不附带任何明示或暗示的担保。AI 生成的人物概述与档案可能存在错误或遗漏，请以原文为准。因使用本软件产生的任何直接或间接损失，作者不承担责任。\n\n" +
            "书籍内容的版权归原作者与出版方所有，本工具仅用于个人阅读辅助。"
    )

    private fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
    }
}
