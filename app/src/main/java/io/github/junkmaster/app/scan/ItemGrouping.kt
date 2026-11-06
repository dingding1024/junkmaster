package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanItem

/**
 * 扫描结果的三分区（第六轮反馈第 4 条）。
 *
 * 原实现把 L1 / L2 / L3 / 仅报告 四段平铺在一个长列表里，
 * 真机上 1856 项要滑很久。改成按「是否需要特殊权限」分三个 Tab，
 * 用户默认只看第一个 Tab 就能完成基础清理。
 */
enum class ResultTab {
    /** 免权限可清理：L1 中不需要 Shizuku 的项。 */
    LOCAL,

    /** 需 Shizuku 深度：requiresShizuku 的 L1 深度缓存 + 全部 L2 需确认项。 */
    DEEP,

    /** 仅报告 / 受保护：Download 大文件、旧 APK、L3 禁止项。 */
    REPORT
}

/**
 * 同一规则下的一组扫描项。
 *
 * 反馈第 3 条：后果说明逐项重复，占满屏幕。改为按规则聚合，
 * **同类型只显示一次后果说明**（挂在组头），组内条目折叠。
 */
data class ItemGroup(
    val ruleId: String,
    /** 分类中文名（同一 ruleId 的 category 恒定）。 */
    val title: String,
    /** 该组统一的后果文案 id，组头只渲染一次。 */
    val consequenceId: String,
    val items: List<ScanItem>,
    val totalBytes: Long,
    val checkedCount: Int,
    /** 组内是否存在可勾选项（L3 / 仅报告恒为 false）。 */
    val checkable: Boolean,
    /** 组内是否全部需要 Shizuku。 */
    val requiresShizuku: Boolean
) {
    val count: Int get() = items.size

    val allChecked: Boolean get() = checkable && checkedCount == items.size

    val partiallyChecked: Boolean get() = checkable && checkedCount in 1 until items.size
}

object ItemGrouping {

    /** 不需要特殊权限、可直接删的项。 */
    fun localItems(items: List<ScanItem>): List<ScanItem> =
        items.filter { !it.requiresShizuku && it.riskLevel == RiskLevel.L1_SAFE }

    /** 需要 Shizuku 才能删的项（深度缓存 + L2 需确认）。 */
    fun deepItems(items: List<ScanItem>): List<ScanItem> =
        items.filter {
            (it.requiresShizuku && it.riskLevel != RiskLevel.L3_FORBIDDEN) ||
                    (it.riskLevel == RiskLevel.L2_CONFIRM && it.deleteMode.canDelete)
        }

    /** 仅报告 / L3 受保护项（永不删除）。 */
    fun reportItems(items: List<ScanItem>): List<ScanItem> =
        items.filter { !it.deleteMode.canDelete || it.riskLevel == RiskLevel.L3_FORBIDDEN }

    fun tabOf(item: ScanItem): ResultTab = when {
        !item.deleteMode.canDelete || item.riskLevel == RiskLevel.L3_FORBIDDEN -> ResultTab.REPORT
        item.requiresShizuku -> ResultTab.DEEP
        item.riskLevel == RiskLevel.L2_CONFIRM -> ResultTab.DEEP
        else -> ResultTab.LOCAL
    }

    fun itemsFor(tab: ResultTab, items: List<ScanItem>): List<ScanItem> = when (tab) {
        ResultTab.LOCAL -> localItems(items)
        ResultTab.DEEP -> deepItems(items)
        ResultTab.REPORT -> reportItems(items)
    }

    /**
     * 按 ruleId 聚合。组内按体积降序（大项先看到），组间按总体积降序再按项数降序，
     * 保证「哪类最占地方」排在最上面。
     */
    fun group(items: List<ScanItem>): List<ItemGroup> =
        items.groupBy { it.ruleId }
            .map { (ruleId, raw) ->
                val sorted = raw.sortedWith(
                    compareByDescending<ScanItem> { it.sizeBytes }.thenBy { it.displayName }
                )
                val checkable = sorted.any { it.deleteMode.canDelete && it.riskLevel.isDeletable }
                ItemGroup(
                    ruleId = ruleId,
                    title = sorted.first().category.label,
                    consequenceId = sorted.first().consequenceId,
                    items = sorted,
                    totalBytes = sorted.sumOf { it.sizeBytes },
                    checkedCount = sorted.count { it.checked && it.deleteMode.canDelete },
                    checkable = checkable,
                    requiresShizuku = sorted.all { it.requiresShizuku }
                )
            }
            .sortedWith(
                compareByDescending<ItemGroup> { it.totalBytes }
                    .thenByDescending { it.count }
                    .thenBy { it.ruleId }
            )
}
