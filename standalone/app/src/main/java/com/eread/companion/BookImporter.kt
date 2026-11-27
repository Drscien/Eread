package com.eread.companion

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

object BookImporter {

    private val chapterLine = Regex(
        "^\\s*(第[0-9零〇一二三四五六七八九十百千万两]+[章节回卷篇话节集].{0,40}|Chapter\\s+\\d+.{0,40}|CHAPTER\\s+\\d+.{0,40})\\s*$",
        setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
    )

    fun import(context: Context, displayName: String, input: InputStream): LocalBook {
        val tmp = File(context.cacheDir, "import_" + System.currentTimeMillis())
        input.use { src -> tmp.outputStream().use { src.copyTo(it) } }
        try {
            val head = ByteArray(4)
            tmp.inputStream().use { it.read(head) }
            val isZip = head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            val name = cleanName(displayName)
            val pieces = if (isZip) parseEpub(tmp) else splitTxt(decode(tmp.readBytes()))
            if (pieces.isEmpty()) error("没有解析到正文")
            return write(contextSnapshot(context), name, pieces)
        } finally {
            tmp.delete()
        }
    }

    private fun contextSnapshot(context: Context): Context = context.applicationContext

    /** 写归一化 UTF-8 正文 + 字节索引 */
    private fun write(context: Context, name: String, pieces: List<Pair<String, String>>): LocalBook {
        val dir = File(context.filesDir, "local").apply { mkdirs() }
        val fileName = "book_" + System.currentTimeMillis() + ".txt"
        val chapters = ArrayList<LocalChapter>(pieces.size)
        FileOutputStream(File(dir, fileName)).use { out ->
            var pos = 0L
            pieces.forEachIndexed { i, piece ->
                val data = piece.second.toByteArray(Charsets.UTF_8)
                out.write(data)
                chapters.add(LocalChapter(i, piece.first, pos, pos + data.size))
                pos += data.size
            }
        }
        return LocalBook(name, fileName, chapters)
    }

    private fun cleanName(displayName: String): String {
        var name = displayName.substringBeforeLast('.').ifBlank { displayName }
        name = name.substringBefore(" 作者").substringBefore("（").substringBefore("(")
        return name.trim()
    }

    // ---------------- TXT ----------------

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        }.getOrElse { String(bytes, Charset.forName("GBK")) }
    }

    private fun splitTxt(text: String): List<Pair<String, String>> {
        val matches = chapterLine.findAll(text).toList()
        if (matches.size >= 2) {
            val out = ArrayList<Pair<String, String>>(matches.size)
            matches.forEachIndexed { i, m ->
                val start = m.range.first
                val end = if (i + 1 < matches.size) matches[i + 1].range.first else text.length
                if (end > start) out.add(m.value.trim() to text.substring(start, end))
            }
            if (out.isNotEmpty()) return out
        }
        val out = ArrayList<Pair<String, String>>()
        var pos = 0
        var index = 1
        while (pos < text.length) {
            var end = (pos + 3000).coerceAtMost(text.length)
            if (end < text.length) {
                val nl = text.indexOf('\n', end)
                if (nl in end until (end + 500)) end = nl
            }
            out.add("第 " + index + " 节" to text.substring(pos, end))
            pos = end
            index++
        }
        return out
    }

    // ---------------- EPUB ----------------

    private fun parseEpub(file: File): List<Pair<String, String>> {
        ZipFile(file).use { zip ->
            val container = readEntry(zip, "META-INF/container.xml") ?: error("不是有效的 EPUB")
            val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1)
                ?: error("找不到 OPF")
            val opf = readEntry(zip, opfPath) ?: error("读不到 OPF")

            val baseDir = opfPath.substringBeforeLast('/', "")
            val manifest = HashMap<String, Pair<String, String>>() // id -> href to mediaType
            Regex("<item\\s+[^>]*>").findAll(opf).forEach { m ->
                val tag = m.value
                val id = attr(tag, "id")
                val href = attr(tag, "href")
                val type = attr(tag, "media-type") ?: ""
                if (id != null && href != null) manifest[id] = href to type
            }

            val spineBlock = Regex("<spine[^>]*>([\\s\\S]*?)</spine>").find(opf)?.groupValues?.get(1).orEmpty()
            val order = Regex("<itemref\\s+[^>]*>").findAll(spineBlock).mapNotNull { attr(it.value, "idref") }.toList()
            if (order.isEmpty()) error("EPUB 没有 spine")

            val bookTitle = Regex("<dc:title[^>]*>([\\s\\S]*?)</dc:title>").find(opf)
                ?.groupValues?.get(1)?.let { stripTags(it) }?.trim().orEmpty()

            val out = ArrayList<Pair<String, String>>()
            order.forEachIndexed { index, idref ->
                val item = manifest[idref] ?: return@forEachIndexed
                if (!item.second.contains("html", true)) return@forEachIndexed
                val path = resolve(baseDir, item.first)
                val html = readEntry(zip, path) ?: return@forEachIndexed
                val text = htmlToText(html)
                if (text.length < 20) return@forEachIndexed
                val heading = Regex("<h[1-3][^>]*>([\\s\\S]*?)</h[1-3]>").find(html)
                    ?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
                val titleTag = Regex("<title[^>]*>([\\s\\S]*?)</title>").find(html)
                    ?.groupValues?.get(1)?.let { stripTags(it) }?.trim()
                val title = heading?.takeIf { it.isNotBlank() }
                    ?: titleTag?.takeIf { it.isNotBlank() && !it.equals(bookTitle, true) }
                    ?: ("第 " + (index + 1) + " 节")
                out.add(title to text)
            }
            if (out.isEmpty()) error("EPUB 里没提取到正文")
            return out
        }
    }

    private fun readEntry(zip: ZipFile, path: String): String? {
        val entry = zip.getEntry(path)
            ?: zip.entries().asSequence().firstOrNull { it.name.equals(path, true) }
            ?: return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }.getOrElse { String(bytes, Charset.forName("GBK")) }
    }

    private fun resolve(baseDir: String, href: String): String {
        val clean = URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        val combined = if (clean.startsWith("/")) clean.trimStart('/') else if (baseDir.isEmpty()) clean else baseDir + "/" + clean
        val parts = ArrayList<String>()
        combined.split('/').forEach { p ->
            when (p) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(p)
            }
        }
        return parts.joinToString("/")
    }

    private fun attr(tag: String, name: String): String? =
        Regex(name + "=\"([^\"]*)\"").find(tag)?.groupValues?.get(1)

    private fun htmlToText(html: String): String {
        var s = html.replace(Regex("(?is)<(script|style|head)[^>]*>[\\s\\S]*?</\\1>"), " ")
        s = s.replace(Regex("(?i)<br\\s*/?>"), "\n")
        s = s.replace(Regex("(?i)</(p|div|h[1-6]|li|tr|blockquote)>"), "\n")
        s = s.replace(Regex("(?s)<[^>]+>"), "")
        s = decodeEntities(s)
        return s.replace(Regex("[ \\t\\u00A0]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun stripTags(s: String): String = decodeEntities(s.replace(Regex("(?s)<[^>]+>"), "")).trim()

    private fun decodeEntities(s: String): String {
        var out = s
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
        out = Regex("&#x([0-9a-fA-F]+);").replace(out) { m ->
            runCatching { String(Character.toChars(m.groupValues[1].toInt(16))) }.getOrDefault("")
        }
        out = Regex("&#(\\d+);").replace(out) { m ->
            runCatching { String(Character.toChars(m.groupValues[1].toInt())) }.getOrDefault("")
        }
        return out
    }
}
