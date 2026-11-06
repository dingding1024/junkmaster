package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/** L1：/sdcard/Android/data/<pkg>/cache 与 code_cache（只读 dry-run）。 */
class AppCacheScanner(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("应用外部 cache / code_cache（L1）", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        val n = entry.path.replace('\\', '/').lowercase()
        if (!n.contains("/android/data/")) return null
        val segments = n.split('/').filter { it.isNotEmpty() }
        val category = when {
            "code_cache" in segments -> ScanCategory.CODE_CACHE
            "cache" in segments -> ScanCategory.APP_CACHE
            else -> return null
        }
        return evaluator.toScanItem(entry.path, entry.sizeBytes, entry.lastModifiedMs, category)
    }
}
