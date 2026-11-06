package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/** 崩溃 / ANR 日志（L1）与系统日志（L2），只读 dry-run。 */
class LogScanner(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("崩溃 / ANR / 系统日志", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        val segments = entry.path.replace('\\', '/').lowercase()
            .split('/').filter { it.isNotEmpty() }
        val category = when {
            segments.any { it == "anr" || it == "tombstones" || it == "traces" } -> ScanCategory.CRASH_LOG
            segments.any { it == "log" || it == "logs" } -> ScanCategory.SYSTEM_LOG
            entry.name.lowercase().endsWith(".log") -> ScanCategory.SYSTEM_LOG
            else -> return null
        }
        return evaluator.toScanItem(entry.path, entry.sizeBytes, entry.lastModifiedMs, category)
    }
}
