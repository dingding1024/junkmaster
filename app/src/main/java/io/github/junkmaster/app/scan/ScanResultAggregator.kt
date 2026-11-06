package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanItem

/** 聚合后的扫描结果：L1 / L2 / L3 / 仅报告 四分区。纯 JVM。 */
data class ScanResult(
    val l1Items: List<ScanItem>,
    val l2Items: List<ScanItem>,
    val l3Items: List<ScanItem>,
    val reportOnlyItems: List<ScanItem>,
    val totalScanned: Int,
    val cancelled: Boolean
) {
    val allItems: List<ScanItem> get() = l1Items + l2Items + l3Items + reportOnlyItems
    /** 可清理字节（L1 + L2，不含仅报告与 L3） */
    val cleanableBytes: Long get() = (l1Items + l2Items).sumOf { it.sizeBytes }
    val l1Bytes: Long get() = l1Items.sumOf { it.sizeBytes }
    val l2Bytes: Long get() = l2Items.sumOf { it.sizeBytes }

    companion object {
        fun empty() = ScanResult(emptyList(), emptyList(), emptyList(), emptyList(), 0, false)
    }
}

/**
 * 结果聚合器：分区 + 默认勾选态 + 安全徽标一致性。
 * 强制：L3 与 REPORT_ONLY 恒不可勾选；L1 默认勾选、L2 默认不勾（由 RuleEvaluator 设定）。
 */
class ScanResultAggregator {

    fun aggregate(items: List<ScanItem>): ScanResult {
        val l1 = mutableListOf<ScanItem>()
        val l2 = mutableListOf<ScanItem>()
        val l3 = mutableListOf<ScanItem>()
        val reportOnly = mutableListOf<ScanItem>()

        items.forEach { raw ->
            val item = normalize(raw)
            when {
                item.riskLevel == RiskLevel.L3_FORBIDDEN || item.deleteMode == DeleteMode.FORBIDDEN -> l3 += item
                item.deleteMode == DeleteMode.REPORT_ONLY -> reportOnly += item
                item.riskLevel == RiskLevel.L1_SAFE -> l1 += item
                else -> l2 += item
            }
        }
        return ScanResult(l1, l2, l3, reportOnly, items.size, false)
    }

    private fun normalize(item: ScanItem): ScanItem {
        val canCheck = item.deleteMode.canDelete && item.riskLevel != RiskLevel.L3_FORBIDDEN
        return item.copy(
            checked = if (canCheck) item.checked else false,
            isSafe = item.riskLevel.safe
        )
    }
}
