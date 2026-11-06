package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.BuiltInRules
import io.github.junkmaster.app.rule.RiskClassifier

/**
 * 超过 1 个月的大文件索引（第七轮反馈第 4 条）。
 *
 * 老板原话：「添加手机超过1个月以上的大文件索引显示，并给出路径方便我查看」。
 *
 * 语义边界（必须守住）：
 *  - **只索引，永不删除**：产物恒为 REPORT_ONLY（或受保护路径的 FORBIDDEN），
 *    绝不进入删除队列，UI 里也不可勾选；
 *  - 判定不靠路径规则，而是「体积 >= [MIN_BYTES] 且最后修改距今 >= [MIN_AGE_DAYS] 天」，
 *    所以任何目录（Download、微信目录、截图目录……）里的大文件都会被看见；
 *  - 相册 / 视频 / DCIM / OBB 等受保护路径照样进索引（用户需要知道它们占了多少空间），
 *    但风险等级仍是 L3、删除模式仍是 FORBIDDEN —— 只读不删的安全语义不破。
 *
 * 纯 JVM，无 android.* 依赖，可被单测直接用假 FileEntry 驱动。
 */
object LargeFileIndex {

    /** 大文件阈值：50 MB。低于它的文件即使很旧也不值得单独索引。 */
    const val MIN_BYTES: Long = 50L * 1024L * 1024L

    /** 老化阈值：30 天（即老板说的「超过 1 个月」）。 */
    const val MIN_AGE_DAYS: Int = 30

    /** 与 BuiltInRules.LARGE_OLD 保持一致；单测会断言两者相同。 */
    const val RULE_ID: String = "report.large_old"

    private const val DAY_MS: Long = 24L * 60L * 60L * 1000L

    /** 距今多少天（向下取整；时间戳非法或未来时间返回 0）。 */
    fun ageDays(lastModifiedMs: Long, nowMs: Long): Int {
        if (lastModifiedMs <= 0L) return 0
        val diff = nowMs - lastModifiedMs
        if (diff <= 0L) return 0
        return (diff / DAY_MS).toInt()
    }

    /**
     * 是否进入索引。mtime 为 0（拿不到时间）一律排除：
     * 无法判断是否「超过 1 个月」，宁可不显示也不误导。
     */
    fun isEligible(sizeBytes: Long, lastModifiedMs: Long, nowMs: Long): Boolean {
        if (lastModifiedMs <= 0L) return false
        if (sizeBytes < MIN_BYTES) return false
        return ageDays(lastModifiedMs, nowMs) >= MIN_AGE_DAYS
    }

    /**
     * 是否已由 [DownloadScanner] 单独报告。
     *
     * Download 目录里的文件、`.apk / .apks / .xapk` 在「仅报告」页已经有自己的分组，
     * 如果大文件索引再收录一次，同一个文件会出现两行、合计体积翻倍，用户会以为要清两遍。
     * 所以索引里排除它们：**同一个文件只在一个分组里出现一次**。
     */
    fun isCoveredByDownloadReport(path: String, name: String): Boolean {
        val segments = path.replace('\\', '/').lowercase().split('/').filter { it.isNotEmpty() }
        if ("download" in segments || "downloads" in segments) return true
        val lower = name.lowercase()
        return lower.endsWith(".apk") || lower.endsWith(".apks") || lower.endsWith(".xapk")
    }

    fun toItem(entry: FileEntry, nowMs: Long): ScanItem {        val rule = BuiltInRules.LARGE_OLD
        val forbidden = RiskClassifier.isForbiddenPath(entry.path)
        val days = ageDays(entry.lastModifiedMs, nowMs)
        return ScanItem(
            id = entry.path,
            path = entry.path,
            displayName = entry.name.ifBlank { entry.path },
            sizeBytes = entry.sizeBytes,
            // 分类固定为「超过 30 天的大文件」，这样同一条索引在结果页聚成一组；
            // 受保护路径也不改分类，只改风险等级 —— 否则相册视频会散进「L3 受保护」组里，
            // 用户就看不到「大文件索引」这个整体了。
            category = ScanCategory.LARGE_FILE_OLD,
            riskLevel = if (forbidden) RiskLevel.L3_FORBIDDEN else RiskLevel.L2_CONFIRM,
            deleteMode = if (forbidden) DeleteMode.FORBIDDEN else DeleteMode.REPORT_ONLY,
            consequenceId = rule.consequenceId,
            ruleId = rule.id,
            lastModifiedMs = entry.lastModifiedMs,
            isSafe = false,
            reason = if (forbidden) {
                "受保护目录（相册 / 视频 / OBB 等）：仅索引，永不删除"
            } else {
                "仅索引，不自动删除；已 $days 天未改动"
            },
            checked = false,
            requiresShizuku = false
        )
    }
}

/**
 * 从已有文件快照里筛出大文件索引项。
 *
 * 复用 [FileSystemWalker] 的快照，**不额外遍历一次文件系统**（多一次全盘遍历在真机上
 * 是明显的额外耗时与发热来源）。只读 dry-run，绝不删除。
 */
class LargeFileScanner(
    private val provider: EntriesProvider,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ItemSource {

    override val name: String = "大文件索引（>=50 MB 且超过 30 天，仅报告）"

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        val nowMs = now()
        provider().forEach { entry ->
            if (session.cancelled) return
            if (entry.isDirectory) return@forEach
            if (!LargeFileIndex.isEligible(entry.sizeBytes, entry.lastModifiedMs, nowMs)) {
                return@forEach
            }
            // 同一个文件只出现一次：Download 目录与 .apk 已由 DownloadScanner 报告
            if (LargeFileIndex.isCoveredByDownloadReport(entry.path, entry.name)) {
                return@forEach
            }
            emit(LargeFileIndex.toItem(entry, nowMs))
        }
    }
}
