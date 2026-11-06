package io.github.junkmaster.app.clean

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 结构化删除日志：App 私有目录 files/clean_logs 下的 jsonl 文件。
 * 只记录路径 / 大小 / 规则 / 批次 / 结果 / 原因，**不记录文件内容、不上传**。
 * 纯 JVM（手写 JSON，避免引入 org.json 等 android.* 依赖）。
 */
data class JournalEntry(
    val timestampMs: Long,
    val path: String,
    val sizeBytes: Long,
    val ruleId: String,
    val batchIndex: Int,
    val success: Boolean,
    val reason: String
)

class DeleteJournal(private val logDir: File) {

    init {
        if (!logDir.exists()) {
            try {
                logDir.mkdirs()
            } catch (t: Throwable) {
                // 目录不可创建时，append 会再次失败并由调用方容忍
            }
        }
    }

    fun append(entry: JournalEntry) {
        val file = File(logDir, "clean_" + dateStamp(entry.timestampMs) + ".jsonl")
        file.appendText(toJson(entry) + "\n", Charsets.UTF_8)
    }

    fun files(): List<File> =
        logDir.listFiles()?.filter { it.isFile && it.name.endsWith(".jsonl") }?.sortedBy { it.name }
            ?: emptyList()

    private fun dateStamp(ts: Long): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    private fun toJson(e: JournalEntry): String = buildString {
        append("{")
        append("\"ts\":").append(e.timestampMs).append(",")
        append("\"path\":\"").append(escape(e.path)).append("\",")
        append("\"size\":").append(e.sizeBytes).append(",")
        append("\"rule\":\"").append(escape(e.ruleId)).append("\",")
        append("\"batch\":").append(e.batchIndex).append(",")
        append("\"ok\":").append(e.success).append(",")
        append("\"reason\":\"").append(escape(e.reason)).append("\"")
        append("}")
    }

    private fun escape(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) {
                    sb.append("\\u")
                    sb.append(c.code.toString(16).padStart(4, '0'))
                } else {
                    sb.append(c)
                }
            }
        }
        return sb.toString()
    }
}
