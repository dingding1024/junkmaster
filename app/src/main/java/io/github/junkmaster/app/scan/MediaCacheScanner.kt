package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * L2：聊天应用附件缓存（/sdcard/Android/media/...）。只读 dry-run。
 * M5 深度模式下由 UserService 的 listCandidates() 提供条目后复用同一 Finder。
 */
class MediaCacheScanner(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("聊天附件缓存（L2）", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        val n = entry.path.replace('\\', '/').lowercase()
        if (!n.contains("/android/media/")) return null
        return evaluator.toScanItem(entry.path, entry.sizeBytes, entry.lastModifiedMs, ScanCategory.CHAT_CACHE)
    }
}
