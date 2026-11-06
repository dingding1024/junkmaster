package io.github.junkmaster.app

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator
import io.github.junkmaster.app.scan.ScanResultAggregator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：L1/L2/L3/仅报告 分区、默认勾选、安全徽标、合计字节 */
class ScanResultAggregatorTest {

    private val evaluator = RuleEvaluator()

    private fun items(): List<ScanItem> = listOf(
        evaluator.toScanItem("/sdcard/Android/data/com.x/cache/a.tmp", 100L),
        evaluator.toScanItem(
            "/sdcard/Android/media/com.tencent.mm/cache/v.amr",
            200L,
            preferCategory = ScanCategory.CHAT_CACHE
        ),
        evaluator.toScanItem("/sdcard/Download/big.zip", 500L),
        evaluator.toScanItem("/data/data/com.x/databases/msg.db", 50L)
    )

    @Test
    fun partitionsIntoFourGroups() {
        val r = ScanResultAggregator().aggregate(items())
        assertEquals(1, r.l1Items.size)
        assertEquals(1, r.l2Items.size)
        assertEquals(1, r.l3Items.size)
        assertEquals(1, r.reportOnlyItems.size)
    }

    @Test
    fun defaultCheckedL1Only() {
        val r = ScanResultAggregator().aggregate(items())
        assertTrue(r.l1Items[0].checked)
        assertFalse(r.l2Items[0].checked)
        assertFalse(r.l3Items[0].checked)
        assertFalse(r.reportOnlyItems[0].checked)
    }

    @Test
    fun safeBadgeOnlyOnL1() {
        val r = ScanResultAggregator().aggregate(items())
        assertTrue(r.l1Items[0].isSafe)
        assertFalse(r.l2Items[0].isSafe)
        assertFalse(r.l3Items[0].isSafe)
        assertFalse(r.reportOnlyItems[0].isSafe)
    }

    @Test
    fun cleanableBytesExcludesReportOnlyAndL3() {
        val r = ScanResultAggregator().aggregate(items())
        assertEquals(300L, r.cleanableBytes)
        assertEquals(100L, r.l1Bytes)
        assertEquals(200L, r.l2Bytes)
    }

    @Test
    fun l3AndReportOnlyNeverCheckedEvenIfInputForcesChecked() {
        val forced = items().map { it.copy(checked = true) }
        val r = ScanResultAggregator().aggregate(forced)
        assertFalse(r.l3Items[0].checked)
        assertFalse(r.reportOnlyItems[0].checked)
    }
}
