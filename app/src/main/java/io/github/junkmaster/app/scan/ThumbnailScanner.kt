package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/** L1：.thumbnails 缩略图缓存（只读 dry-run）。 */
class ThumbnailScanner(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("缩略图（L1）", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        val n = entry.path.replace('\\', '/').lowercase()
        val hit = n.contains("/.thumbnails/") || n.contains("/thumbnails/") ||
                entry.name.lowercase().endsWith(".thumb")
        if (!hit) return null
        return evaluator.toScanItem(entry.path, entry.sizeBytes, entry.lastModifiedMs, ScanCategory.THUMBNAIL)
    }
}
