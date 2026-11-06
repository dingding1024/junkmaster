package io.github.junkmaster.app.diag

import io.github.junkmaster.app.scan.ScanResult
import java.io.File

/**
 * 「最近一次扫描分布」摘要：写 App 私有目录 files/diag/last_scan.txt，供「测试诊断」页读取。
 *
 * 纯 JVM、纯文本 key=value，**只输出数量与代码里的 ruleId 常量，不含任何路径、
 * 文件名、第三方包名或文件内容**，因此可以直接进入脱敏诊断报告。
 *
 * 为什么需要（2026-11-05 第四轮真机反馈）：vc4 报告只有清理日志的 rule 计数，
 * `rules=l1.empty_dir=76` 只能说明「删了什么」，看不出「扫描到底产出了什么」。
 * 有了这一段，一份报告就能判断基础扫描是否覆盖了有体积的缓存文件、
 * 有多少项落在 L3 禁止区 / 仅报告区 / Shizuku 深度区，不必再靠截图猜。
 */
object LastScanSummary {

    const val FILE_NAME: String = "last_scan.txt"

    fun render(result: ScanResult, nowMs: Long): String = buildString {
        append("ts=").append(nowMs).append('\n')
        append("total=").append(result.totalScanned).append('\n')
        append("l1=").append(result.l1Items.size).append('\n')
        append("l2=").append(result.l2Items.size).append('\n')
        append("l3=").append(result.l3Items.size).append('\n')
        append("report_only=").append(result.reportOnlyItems.size).append('\n')
        append("l1_checked=").append(result.l1Items.count { it.checked }).append('\n')
        append("l1_bytes=").append(result.l1Bytes).append('\n')
        append("l2_bytes=").append(result.l2Bytes).append('\n')
        append("l1_requires_shizuku=").append(result.l1Items.count { it.requiresShizuku }).append('\n')
        append("l1_bytes_requires_shizuku=")
            .append(result.l1Items.filter { it.requiresShizuku }.sumOf { it.sizeBytes }).append('\n')
        append("categories=").append(
            result.allItems.groupingBy { it.ruleId }.eachCount()
                .entries.sortedBy { it.key }
                .joinToString(", ") { it.key + "=" + it.value }
                .ifBlank { "none" }
        ).append('\n')
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
}
