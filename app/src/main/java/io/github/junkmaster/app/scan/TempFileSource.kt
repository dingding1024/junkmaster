package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/** L1 基础安全项：.tmp / .temp / .part / .partial / ~ 临时文件（只读 dry-run）。 */
class TempFileSource(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("临时文件（L1 基础）", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        if (!TempFileFilter.isTemp(entry.name)) return null
        return evaluator.toScanItem(
            entry.path,
            entry.sizeBytes,
            entry.lastModifiedMs,
            ScanCategory.TEMP_FILE
        )
    }
}
