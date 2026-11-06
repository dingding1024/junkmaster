package io.github.junkmaster.app

import io.github.junkmaster.app.diag.LastScanSummary
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.rule.RuleEvaluator
import io.github.junkmaster.app.scan.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 诊断报告 format=3 的「最近一次扫描分布」：必须能自证扫描产出，且绝不含任何路径。 */
class LastScanSummaryTest {

    private val ev = RuleEvaluator()
    private fun l1(path: String) = ev.toScanItem(path, 100L, 0L, ScanCategory.TEMP_FILE)
    private fun l3(path: String) = ev.toScanItem(path, 5L, 0L, ScanCategory.L3_PROTECTED)

    @Test
    fun distributionShowsCountsAndRuleIdsWithoutAnyPath() {
        val r = ScanResult(
            l1Items = listOf(l1("/sdcard/App/cache/a.tmp"), l1("/sdcard/App/cache/b.tmp")),
            l2Items = emptyList(),
            l3Items = listOf(l3("/sdcard/DCIM/.thumbnails/x.thumb")),
            reportOnlyItems = emptyList(),
            totalScanned = 3,
            cancelled = false
        )
        val text = LastScanSummary.render(r, 1793884430624L)
        assertTrue(text.contains("total=3"))
        assertTrue(text.contains("l1=2"))
        assertTrue(text.contains("l3=1"))
        assertTrue(text.contains("l1_bytes=200"))
        assertTrue(text.contains("l1_checked=2"))
        assertTrue(text.contains("categories="))
        assertTrue(text.contains("l1.temp_file=2"))
        assertFalse("摘要不得含路径", text.contains("/sdcard"))
        assertFalse("摘要不得含第三方包名", text.contains("com."))
        assertFalse("摘要不得含文件名", text.contains("a.tmp"))
    }

    @Test
    fun emptyScanStillProducesReadableSummary() {
        val text = LastScanSummary.render(ScanResult.empty(), 1L)
        assertTrue(text.contains("total=0"))
        assertTrue(text.contains("categories=none"))
    }

    @Test
    fun writeThenReadRoundTrip() {
        val dir = Files.createTempDirectory("jm-lastscan").toFile()
        val file = File(dir, LastScanSummary.FILE_NAME)
        val text = LastScanSummary.render(ScanResult.empty(), 42L)
        LastScanSummary.write(file, text)
        assertEquals(text, LastScanSummary.read(file))
        dir.deleteRecursively()
    }
}
