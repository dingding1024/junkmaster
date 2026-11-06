package io.github.junkmaster.app

import io.github.junkmaster.app.diag.ReportSanitizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：诊断报告脱敏，确保外发文本不含真实包名 / 文件名 / 完整绝对路径。 */
class ReportSanitizerTest {

    @Test
    fun `hides third party package names in android data paths`() {
        val raw = "/sdcard/Android/data/com.tencent.mm/cache/voice.amr"
        val out = ReportSanitizer.sanitizePath(raw)
        assertFalse("不得泄露第三方包名", out.contains("com.tencent.mm"))
        assertFalse("不得泄露用户文件名", out.contains("voice.amr"))
        assertTrue(out.contains("<pkg>"))
        assertTrue(out.contains("<file>.amr"))
    }

    @Test
    fun `normalizes shared storage prefixes`() {
        val a = ReportSanitizer.sanitizePath("/storage/emulated/0/Download/old.apk")
        val b = ReportSanitizer.sanitizePath("/sdcard/Download/old.apk")
        assertTrue(a.contains("/<storage>/"))
        assertTrue(b.contains("/<storage>/"))
        assertFalse(a.contains("old.apk"))
        assertFalse(b.contains("old.apk"))
    }

    @Test
    fun `hides private app data directories`() {
        val out = ReportSanitizer.sanitizePath("/data/data/io.github.junkmaster.app/files/clean_logs/clean_20261105.jsonl")
        assertFalse(out.contains("io.github.junkmaster.app"))
        assertFalse(out.contains("clean_20261105.jsonl"))
        assertTrue(out.contains("<pkg>"))
    }

    @Test
    fun `sanitize handles multiline text line by line without swallowing the tail`() {
        val raw = "line1=/sdcard/Download/a.tmp\nline2=plain text\nline3=uid=2000"
        val out = ReportSanitizer.sanitize(raw, 4096)
        assertTrue(out.contains("line1="))
        assertTrue(out.contains("line2=plain text"))
        assertTrue(out.contains("line3=uid=2000"))
        assertFalse(out.contains("a.tmp"))
    }

    @Test
    fun `sanitize strips control chars and truncates`() {
        val raw = "abc\u0000def" + "x".repeat(100)
        val out = ReportSanitizer.sanitize(raw, 20)
        assertFalse(out.contains('\u0000'))
        assertTrue(out.startsWith("abcdef"))
        assertTrue(out.endsWith("…(已截断)"))
    }

    @Test
    fun `aggregate journal only counts and never returns paths`() {
        val lines = sequenceOf(
            "{\"ts\":1000,\"path\":\"/sdcard/Download/secret1.tmp\",\"rule\":\"temp\",\"ok\":true}",
            "{\"ts\":2000,\"path\":\"/sdcard/Android/data/com.x/cache/secret2.dat\",\"rule\":\"app_cache\",\"ok\":true}",
            "{\"ts\":3000,\"path\":\"/sdcard/Download/secret3.apk\",\"rule\":\"temp\",\"ok\":false}"
        )
        val s = ReportSanitizer.aggregateJournal(lines)
        assertEquals(3, s.entryCount)
        assertEquals(2, s.successCount)
        assertEquals(1, s.failedCount)
        assertEquals(2, s.ruleCounts["temp"])
        assertEquals(1, s.ruleCounts["app_cache"])
        assertEquals(3000L, s.latestTs)
        assertFalse(s.toString().contains("secret"))
    }

    @Test
    fun `aggregate journal tolerates malformed lines`() {
        val s = ReportSanitizer.aggregateJournal(sequenceOf("", "not-json", "{\"rule\":\"x\",\"ok\":true}"))
        assertEquals(2, s.entryCount)
        assertEquals(1, s.successCount)
        assertEquals(1, s.ruleCounts["x"])
        assertEquals(1, s.ruleCounts["unknown"])
    }

    @Test
    fun `aggregate journal exposes failure reasons without any path`() {
        // 老板第三轮反馈：光有 failed=1 无法定位，报告必须能自证失败原因
        val lines = sequenceOf(
            "{\"ts\":1000,\"path\":\"/sdcard/Android/data/com.secret/cache/a.tmp\",\"rule\":\"l1.empty_dir\",\"ok\":false,\"reason\":\"复核拒绝：共享存储结构目录禁止删除\"}",
            "{\"ts\":2000,\"path\":\"/sdcard/Download/private.tmp\",\"rule\":\"l1.temp_file\",\"ok\":false,\"reason\":\"删除失败：AccessDeniedException\"}",
            "{\"ts\":3000,\"path\":\"/sdcard/Download/private2.tmp\",\"rule\":\"l1.temp_file\",\"ok\":false,\"reason\":\"删除失败：AccessDeniedException\"}"
        )
        val s = ReportSanitizer.aggregateJournal(lines)
        assertEquals(3, s.failedCount)
        assertEquals(2, s.failedReasonCounts["删除失败：AccessDeniedException"])
        assertEquals(1, s.failedReasonCounts["复核拒绝：共享存储结构目录禁止删除"])
        assertEquals("删除失败：AccessDeniedException", s.lastReason)
        assertEquals("l1.temp_file", s.lastRule)
        assertEquals(false, s.lastOk)
        assertFalse(s.toString().contains("secret"))
        assertFalse(s.toString().contains("private.tmp"))
    }

    @Test
    fun `reason lines survive sanitize as plain text`() {
        val out = ReportSanitizer.sanitize(
            "fail_reasons=删除失败：AccessDeniedException x2 | 复核拒绝：共享存储结构目录禁止删除 x1",
            1024
        )
        assertTrue(out.contains("AccessDeniedException"))
        assertTrue(out.contains("结构目录"))
    }

    @Test
    fun `safe skips are separated from hard failures`() {
        // 第四轮真机：旧报告把 8 次「目录已非空，已跳过」显示成 failed=8
        val lines = sequenceOf(
            "{\"ts\":1000,\"path\":\"/sdcard/Download/a.tmp\",\"rule\":\"l1.empty_dir\",\"ok\":false,\"reason\":\"安全跳过：目录已非空，已跳过（防误删）\"}",
            "{\"ts\":2000,\"path\":\"/sdcard/Download/b.tmp\",\"rule\":\"l1.empty_dir\",\"ok\":false,\"reason\":\"安全跳过：目录已非空，已跳过（防误删）\"}",
            "{\"ts\":3000,\"path\":\"/sdcard/Download/c.tmp\",\"rule\":\"l1.temp_file\",\"ok\":false,\"reason\":\"删除失败：AccessDeniedException\"}",
            "{\"ts\":4000,\"path\":\"/sdcard/Download/d.tmp\",\"rule\":\"l1.temp_file\",\"ok\":true}"
        )
        val s = ReportSanitizer.aggregateJournal(lines)
        assertEquals(4, s.entryCount)
        assertEquals(1, s.successCount)
        assertEquals(2, s.skippedCount)
        assertEquals(1, s.failedCount)
        assertEquals(1, s.failedReasonCounts["删除失败：AccessDeniedException"])
        assertEquals(2, s.skippedReasonCounts["安全跳过：目录已非空，已跳过（防误删）"])
        assertFalse(s.toString().contains("a.tmp"))
    }
}
