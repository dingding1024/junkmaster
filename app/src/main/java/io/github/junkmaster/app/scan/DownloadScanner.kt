package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * Download 大文件 / 旧 APK：**恒为 REPORT_ONLY，只报告不删**（硬性约束）。
 * 只读 dry-run，产出项永不进入删除队列。
 */
class DownloadScanner(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("Download 大文件 / 旧 APK（仅报告）", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        val segments = entry.path.replace('\\', '/').lowercase()
            .split('/').filter { it.isNotEmpty() }
        val isDownload = "download" in segments || "downloads" in segments
        val isApk = listOf(".apk", ".apks", ".xapk").any {
            entry.name.lowercase().endsWith(it)
        }
        if (!isDownload && !isApk) return null
        val category = if (isApk) ScanCategory.OLD_APK else ScanCategory.DOWNLOAD_LARGE
        return evaluator.toScanItem(entry.path, entry.sizeBytes, entry.lastModifiedMs, category)
    }
}
