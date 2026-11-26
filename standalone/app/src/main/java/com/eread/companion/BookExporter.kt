package com.eread.companion

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** 把一本书的人物档案与缓存打包成 zip，内含 txt 或 docx。 */
object BookExporter {

    enum class Format { TXT, DOCX }

    fun export(context: Context, states: List<BookState>, format: Format): List<String> {
        val results = ArrayList<String>()
        states.forEach { st ->
            val fileName = safe(st.bookName) + "-" + stamp() + ".zip"
            val bytes = buildZip(st, format)
            val path = save(context, fileName, bytes)
            if (path != null) results.add(path)
        }
        return results
    }

    fun exportToCache(context: Context, states: List<BookState>, format: Format): List<File> {
        val dir = File(context.cacheDir, "exports")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        return states.map { st ->
            val f = File(dir, safe(st.bookName) + "-" + stamp() + ".zip")
            f.writeBytes(buildZip(st, format))
            f
        }
    }

    private fun buildZip(st: BookState, format: Format): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
            put("meta.json", meta(st).toByteArray())
            val ext = if (format == Format.DOCX) "docx" else "txt"
            put("档案合集." + ext, document(st, format, wholeBook = true))
            st.cards.values.forEach { c ->
                put("人物/" + safe(c.name) + "." + ext, document(st, format, wholeBook = false, only = c))
            }
            if (st.rereads.isNotEmpty()) {
                val sb = StringBuilder("回读缓存\n\n")
                st.rereads.forEach { r ->
                    sb.append("[").append(r.name).append("] 位置 ").append(r.bucket * 2000)
                        .append("\n").append(r.summary).append("\n\n")
                }
                put("回读缓存.txt", sb.toString().toByteArray())
            }
        }
        return bos.toByteArray()
    }

    private fun meta(st: BookState): String {
        val sb = StringBuilder()
        sb.append("{\n")
        sb.append("  \"bookName\": \"").append(esc(st.bookName)).append("\",\n")
        sb.append("  \"bookUrl\": \"").append(esc(st.bookUrl)).append("\",\n")
        sb.append("  \"latestPos\": ").append(st.latestPos).append(",\n")
        sb.append("  \"currentPos\": ").append(st.currentPos).append(",\n")
        sb.append("  \"exportedAt\": \"").append(stamp()).append("\",\n")
        sb.append("  \"characters\": [\n")
        st.cards.values.forEachIndexed { i, c ->
            sb.append("    {\"name\": \"").append(esc(c.name)).append("\", ")
                .append("\"importance\": \"").append(esc(c.importance)).append("\", ")
                .append("\"pos\": ").append(c.pos).append(", ")
                .append("\"updates\": ").append(c.updates).append(", ")
                .append("\"hasBiography\": ").append(c.biography.isNotBlank()).append("}")
            if (i < st.cards.size - 1) sb.append(",")
            sb.append("\n")
        }
        sb.append("  ]\n}")
        return sb.toString()
    }

    private fun document(st: BookState, format: Format, wholeBook: Boolean, only: Card? = null): ByteArray {
        val blocks = ArrayList<Block>()
        if (wholeBook) {
            blocks.add(Block(st.bookName, heading = true))
            blocks.add(Block("导出时间：" + stamp()))
            blocks.add(Block("最新位置：" + st.latestPos + "　当前位置：" + st.currentPos))
            blocks.add(Block("人物 " + st.cards.size + " 人"))
            st.cards.values.forEach { c ->
                blocks.add(Block(c.name + "（" + c.importance + "）", heading = true))
                body(blocks, c)
            }
        } else if (only != null) {
            blocks.add(Block(only.name, heading = true))
            body(blocks, only)
        }
        return if (format == Format.DOCX) docx(blocks) else txt(blocks).toByteArray()
    }

    private fun body(blocks: MutableList<Block>, c: Card) {
        blocks.add(Block("重要性：" + c.importance + "　位置：" + c.pos + "　更新 " + c.updates + " 次"))
        blocks.add(Block("最新概述", heading = true))
        blocks.add(Block(c.summary.ifBlank { "（暂无）" }))
        blocks.add(Block("详细档案", heading = true))
        blocks.add(Block(c.biography.ifBlank { "（暂无）" }))
    }

    private fun txt(blocks: List<Block>): String =
        blocks.joinToString("\n\n") { if (it.heading) it.text + "\n" + "—".repeat(8) else it.text }

    private fun docx(blocks: List<Block>): ByteArray {
        val body = StringBuilder()
        blocks.forEach { b ->
            body.append("<w:p><w:r>")
            if (b.heading) body.append("<w:rPr><w:b/><w:sz w:val=\"28\"/></w:rPr>")
            body.append("<w:t xml:space=\"preserve\">").append(esc(b.text)).append("</w:t>")
            body.append("</w:r></w:p>")
        }
        val document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
            "<w:body>" + body + "</w:body></w:document>"
        val contentTypes = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "</Types>"
        val rels = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            fun put(name: String, text: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("word/document.xml", document)
        }
        return bos.toByteArray()
    }

    private fun save(context: Context, fileName: String, bytes: ByteArray): String? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Eread")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/Eread/" + fileName
            } else {
                val dir = File(context.getExternalFilesDir(null), "Eread")
                dir.mkdirs()
                val f = File(dir, fileName)
                f.outputStream().use { it.write(bytes) }
                f.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun safe(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(60).ifBlank { "book" }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private data class Block(val text: String, val heading: Boolean = false)
}
