package io.github.junkmaster.app.clean

import java.io.File

/**
 * 「最近一次清理结果」摘要：写 App 私有目录 files/diag/last_clean.txt，供「测试诊断」页读取。
 *
 * 纯 JVM、纯文本 key=value，**不含任何路径、文件名、包名或文件内容**，因此可以直接
 * 脱敏后进入诊断报告。目的是让老板回传的一份报告就能自证「上一轮清理为什么是 0」，
 * 不需要 adb、不需要截图、也不需要逐条输出清理日志。
 *
 * report_format=3 增加 skipped / empty_dirs_deleted，用来区分
 * 「安全跳过被误当成失败」与「清理的全是不占空间的空目录」两种情形。
 */
object LastCleanSummary {

    const val FILE_NAME: String = "last_clean.txt"

    fun render(report: CleanReport, nowMs: Long): String = buildString {
        append("ts=").append(nowMs).append('\n')
        append("requested=").append(report.requested).append('\n')
        append("deleted=").append(report.deletedCount).append('\n')
        append("empty_dirs_deleted=").append(report.emptyDirDeletedCount).append('\n')
        append("freed_bytes=").append(report.freedBytes).append('\n')
        append("failed=").append(report.failedCount).append('\n')
        append("skipped=").append(report.skippedCount).append('\n')
        append("guard_rejected=").append(report.guardRejectedCount).append('\n')
        append("only_empty_dirs=").append(report.onlyEmptyDirs).append('\n')
        append("batches=").append(report.batches).append('\n')
        append("stopped_early=").append(report.stoppedEarly).append('\n')
        append("capacity_delta=").append(report.capacityDelta ?: "n/a").append('\n')
        append("stop_reason=").append(oneLine(report.stopReason)).append('\n')
        append("guard_reasons=").append(oneLine(report.guardRejectReasons.joinToString(" | "))).append('\n')
        append("skipped_reasons=").append(oneLine(report.skippedReasons.joinToString(" | "))).append('\n')
        append("failed_reasons=").append(oneLine(report.failedReasons.joinToString(" | "))).append('\n')
    }

    fun write(file: File, text: String) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(text, Charsets.UTF_8)
        }
    }

    fun read(file: File): String = try {
        if (file.isFile) file.readText(Charsets.UTF_8) else ""
    } catch (t: Throwable) {
        ""
    }

    private fun oneLine(raw: String): String = raw
        .replace('\r', ' ')
        .replace('\n', ' ')
        .replace('=', '：')
        .ifBlank { "none" }
        .take(300)
}
