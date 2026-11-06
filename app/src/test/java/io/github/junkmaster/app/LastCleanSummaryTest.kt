package io.github.junkmaster.app

import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.clean.LastCleanSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** 诊断报告自证段：摘要必须无路径、可落盘回读、且如实反映「0 删除」的原因。 */
class LastCleanSummaryTest {

    private fun report() = CleanReport(
        requested = 1,
        deletedCount = 0,
        freedBytes = 0L,
        failedCount = 1,
        batches = 1,
        stoppedEarly = false,
        stopReason = "",
        capacityBefore = 100L,
        capacityAfter = 100L,
        guardRejectedCount = 1,
        guardRejectReasons = listOf("共享存储结构目录禁止删除（防误删骨架目录）"),
        failedReasons = listOf("目录不可列举（受保护或无权限），已跳过")
    )

    @Test
    fun summaryContainsNoPathOrFileName() {
        val text = LastCleanSummary.render(report(), 1793874456935L)
        assertFalse(text.contains("/sdcard"))
        assertFalse(text.contains("com."))
        assertFalse(text.contains(".tmp"))
        assertTrue(text.contains("requested=1"))
        assertTrue(text.contains("deleted=0"))
        assertTrue(text.contains("failed=1"))
        assertTrue(text.contains("guard_rejected=1"))
        assertTrue(text.contains("stopped_early=false"))
    }

    @Test
    fun reasonsAreIncludedSoReportCanSelfDiagnose() {
        val text = LastCleanSummary.render(report(), 1L)
        assertTrue(text.contains("结构目录"))
        assertTrue(text.contains("目录不可列举"))
    }

    @Test
    fun writeThenReadRoundTrip() {
        val dir = Files.createTempDirectory("jm-lastclean").toFile()
        val file = java.io.File(dir, LastCleanSummary.FILE_NAME)
        val text = LastCleanSummary.render(report(), 42L)
        LastCleanSummary.write(file, text)
        assertEquals(text, LastCleanSummary.read(file))
        dir.deleteRecursively()
    }

    @Test
    fun missingFileReadsAsBlank() {
        val dir = Files.createTempDirectory("jm-lastclean-missing").toFile()
        assertEquals("", LastCleanSummary.read(java.io.File(dir, "none.txt")))
        dir.deleteRecursively()
    }

    @Test
    fun skipAndEmptyDirCountersAreReportedSoZeroBytesIsExplainable() {
        val r = CleanReport(
            requested = 178,
            deletedCount = 67,
            freedBytes = 0L,
            failedCount = 1,
            batches = 2,
            stoppedEarly = false,
            stopReason = "",
            capacityBefore = 100L,
            capacityAfter = 100L,
            skippedCount = 8,
            skippedReasons = listOf("目录已非空，已跳过（防误删）"),
            emptyDirDeletedCount = 67
        )
        val text = LastCleanSummary.render(r, 1793884430624L)
        assertTrue(text.contains("skipped=8"))
        assertTrue(text.contains("empty_dirs_deleted=67"))
        assertTrue(text.contains("only_empty_dirs=true"))
        assertTrue(text.contains("目录已非空"))
        assertFalse(text.contains("/sdcard"))
        assertFalse(text.contains("com."))
    }
}
