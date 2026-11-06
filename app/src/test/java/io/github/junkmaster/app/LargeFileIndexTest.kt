package io.github.junkmaster.app

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.rule.BuiltInRules
import io.github.junkmaster.app.rule.ConsequenceCatalog
import io.github.junkmaster.app.scan.FileEntry
import io.github.junkmaster.app.scan.LargeFileIndex
import io.github.junkmaster.app.scan.LargeFileScanner
import io.github.junkmaster.app.scan.ScanSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大文件索引（第七轮反馈第 4 条：「手机超过 1 个月以上的大文件索引显示，并给出路径」）。
 *
 * 关键不变量：
 *  1. 判定只靠「体积 >= 50 MB 且最后修改 >= 30 天」，拿不到 mtime 的一律排除（宁可不显示也不误导）；
 *  2. **永不删除**：产物恒为 REPORT_ONLY 或 FORBIDDEN，不可勾选；
 *  3. 受保护目录（相册 / 视频 / OBB）照样进索引，但风险级别是 L3；
 *  4. 同一个文件不能出现两次：Download 与 .apk 已由 DownloadScanner 报告，索引里必须排除。
 */
class LargeFileIndexTest {

    private val day = 24L * 60L * 60L * 1000L
    private val now = 1_800_000_000_000L

    private fun entry(
        path: String,
        size: Long = 80L * 1024L * 1024L,
        mtime: Long = now - 40L * day,
        isDir: Boolean = false
    ) = FileEntry(
        path = path,
        name = path.substringAfterLast('/').ifBlank { path },
        isDirectory = isDir,
        sizeBytes = size,
        lastModifiedMs = mtime
    )

    // ---- 天数计算 ----

    @Test
    fun ageDays_floorAndGuardIllegalInput() {
        assertEquals(40, LargeFileIndex.ageDays(now - 40L * day, now))
        // 差 1 毫秒不到 41 天，必须向下取整为 40，不能四舍五入
        assertEquals(40, LargeFileIndex.ageDays(now - 40L * day - 1L, now))
        // 未来时间戳（时钟回拨 / 文件被改成未来）不能算成负数
        assertEquals(0, LargeFileIndex.ageDays(now + 5L * day, now))
        assertEquals(0, LargeFileIndex.ageDays(0L, now))
    }

    // ---- 入选条件 ----

    @Test
    fun isEligible_requiresBothSizeAndAge() {
        val old = now - 40L * day
        assertTrue(LargeFileIndex.isEligible(LargeFileIndex.MIN_BYTES, old, now))
        // 差 1 字节不算大文件
        assertFalse(LargeFileIndex.isEligible(LargeFileIndex.MIN_BYTES - 1, old, now))
        // 大但不够旧
        assertFalse(LargeFileIndex.isEligible(200L * 1024L * 1024L, now - 29L * day, now))
        // 刚好 30 天：老板说的是「超过 1 个月」，30 天整按含边界处理
        assertTrue(LargeFileIndex.isEligible(LargeFileIndex.MIN_BYTES, now - 30L * day, now))
        // 拿不到修改时间：无法判断是否超过 1 个月，一律不显示
        assertFalse(LargeFileIndex.isEligible(500L * 1024L * 1024L, 0L, now))
    }

    // ---- 去重：同一个文件只出现一次 ----

    @Test
    fun isCoveredByDownloadReport_downloadAndApkAreExcluded() {
        assertTrue(LargeFileIndex.isCoveredByDownloadReport("/storage/emulated/0/Download/a.mp4", "a.mp4"))
        assertTrue(LargeFileIndex.isCoveredByDownloadReport("/storage/emulated/0/downloads/a.zip", "a.zip"))
        assertTrue(LargeFileIndex.isCoveredByDownloadReport("/storage/emulated/0/Movies/Old.APK", "Old.APK"))
        // 普通目录里的普通文件，索引里应当照常出现
        assertFalse(
            LargeFileIndex.isCoveredByDownloadReport("/storage/emulated/0/WeiXin/video.mp4", "video.mp4")
        )
    }

    // ---- 产物语义：只索引、不删除 ----

    @Test
    fun toItem_isAlwaysReportOnlyAndNeverChecked() {
        val item = LargeFileIndex.toItem(entry("/storage/emulated/0/WeiXin/huge.mp4"), now)
        assertEquals(DeleteMode.REPORT_ONLY, item.deleteMode)
        assertEquals(RiskLevel.L2_CONFIRM, item.riskLevel)
        assertFalse("仅索引项绝不能默认勾选", item.checked)
        assertTrue("仅报告项不可勾选", !item.deleteMode.canDelete)
        assertFalse(item.isSafe)
        // 完整路径必须原样带出，老板要靠它自己去文件管理器找
        assertEquals("/storage/emulated/0/WeiXin/huge.mp4", item.path)
        assertTrue("原因里要说明多久没改动", item.reason.contains("40"))
    }

    @Test
    fun toItem_protectedDirStaysIndexedButForbidden() {
        val item = LargeFileIndex.toItem(entry("/storage/emulated/0/DCIM/Camera/old.mp4"), now)
        // 受保护路径也要出现在索引里（用户需要知道相册占了多少），但永远不能删
        assertEquals(ScanCategory.LARGE_FILE_OLD, item.category)
        assertEquals(RiskLevel.L3_FORBIDDEN, item.riskLevel)
        assertEquals(DeleteMode.FORBIDDEN, item.deleteMode)
        assertFalse(item.checked)
    }

    @Test
    fun toItem_ruleAndConsequenceAreConsistent() {
        val item = LargeFileIndex.toItem(entry("/storage/emulated/0/a.bin"), now)
        assertEquals(BuiltInRules.LARGE_OLD.id, item.ruleId)
        assertEquals(LargeFileIndex.RULE_ID, item.ruleId)
        assertEquals(BuiltInRules.LARGE_OLD.consequenceId, item.consequenceId)
        // 后果文案必须真的存在，否则 UI 会显示空白卡片
        assertNotEquals("", ConsequenceCatalog.byId(item.consequenceId).summary)
    }

    // ---- Scanner ----

    @Test
    fun scanner_filtersDirectoriesTinyAndFreshFiles() {
        val entries = listOf(
            entry("/storage/emulated/0/a/big_old.bin"),
            entry("/storage/emulated/0/a/big_fresh.bin", mtime = now - 3L * day),
            entry("/storage/emulated/0/a/small_old.bin", size = 1024L),
            entry("/storage/emulated/0/a/dir_old", isDir = true),
            entry("/storage/emulated/0/a/no_mtime.bin", mtime = 0L)
        )
        val out = mutableListOf<String>()
        LargeFileScanner({ entries }, now = { now })
            .collect(ScanSession()) { out += it.path }
        assertEquals(listOf("/storage/emulated/0/a/big_old.bin"), out)
    }

    @Test
    fun scanner_skipsEntriesAlreadyReportedByDownloadScanner() {
        val entries = listOf(
            entry("/storage/emulated/0/Download/movie.mp4"),
            entry("/storage/emulated/0/Backup/setup.apk"),
            entry("/storage/emulated/0/Backup/archive.zip")
        )
        val out = mutableListOf<String>()
        LargeFileScanner({ entries }, now = { now })
            .collect(ScanSession()) { out += it.path }
        assertEquals(listOf("/storage/emulated/0/Backup/archive.zip"), out)
    }

    @Test
    fun scanner_stopsWhenSessionCancelled() {
        val session = ScanSession()
        session.cancelled = true
        val out = mutableListOf<String>()
        LargeFileScanner({ listOf(entry("/storage/emulated/0/a/big.bin")) }, now = { now })
            .collect(session) { out += it.path }
        assertTrue("取消后不得再产出任何项", out.isEmpty())
    }
}
