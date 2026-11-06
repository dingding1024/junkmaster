package io.github.junkmaster.app

import io.github.junkmaster.app.clean.CapacityProbe
import io.github.junkmaster.app.clean.CleanExecutor
import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.clean.DeleteJournal
import io.github.junkmaster.app.clean.DeleteOutcome
import io.github.junkmaster.app.clean.FileDeleter
import io.github.junkmaster.app.clean.FailurePolicy
import io.github.junkmaster.app.clean.StorageRoots
import io.github.junkmaster.app.clean.combine
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.rule.RuleEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** 纯 JVM 单测：分批 50 / 单项失败跳过 / 连续失败 fail-closed / 复核拒绝 / 日志 / REPORT_ONLY 不入队 */
class CleanExecutorTest {

    private val evaluator = RuleEvaluator()

    private fun l1(path: String, size: Long = 1L, checked: Boolean = true) =
        evaluator.toScanItem(path, size).copy(checked = checked)

    private fun emptyDir(path: String) =
        evaluator.toScanItem(path, 0L, 0L, ScanCategory.EMPTY_DIR).copy(checked = true)

    private class FakeDeleter(private val failOn: Set<String> = emptySet()) : FileDeleter {
        val deleted = mutableListOf<String>()
        override fun delete(path: String, isDirectory: Boolean): DeleteOutcome =
            if (path in failOn) {
                DeleteOutcome(false, "模拟失败")
            } else {
                deleted += path
                DeleteOutcome(true, "ok")
            }
    }

    private class FakeCapacity : CapacityProbe {
        val queried = mutableListOf<String>()
        override fun usableBytes(path: String): Long {
            queried += path
            return 10_000L
        }
        override fun totalBytes(path: String) = 20_000L
    }

    @Test
    fun batchesOfFifty() {
        val items = (0 until 120).map { l1("/sdcard/Android/data/com.x/cache/f$it.tmp") }
        val deleter = FakeDeleter()
        val r = CleanExecutor(deleter).execute(items)
        assertEquals(120, r.requested)
        assertEquals(120, r.deletedCount)
        assertEquals(3, r.batches)
        assertFalse(r.stoppedEarly)
        assertEquals(120, deleter.deleted.size)
    }

    @Test
    fun singleDeletionFailureSkipsThatItemAndKeepsCleaning() {
        // 老板真机第三轮反馈：一个受保护目录删除失败，不能让整批空转成 0 删除
        val items = (0 until 10).map { l1("/sdcard/Android/data/com.x/cache/f$it.tmp") }
        val failPath = items[2].path
        val deleter = FakeDeleter(setOf(failPath))
        val r = CleanExecutor(deleter).execute(items)
        assertEquals(9, r.deletedCount)
        assertEquals(1, r.failedCount)
        assertFalse("单项失败不应中止整批", r.stoppedEarly)
        assertEquals(1, r.batches)
        assertEquals(9, deleter.deleted.size)
        assertTrue(r.failedReasons.contains("模拟失败"))
    }

    @Test
    fun consecutiveFailuresTriggerFailClosedStop() {
        // 系统性异常（权限被撤 / 存储不可用）仍必须中止，不能无限跳过
        val items = (0 until 8).map { l1("/sdcard/Android/data/com.x/cache/f$it.tmp") }
        val deleter = FakeDeleter(items.map { it.path }.toSet())
        val r = CleanExecutor(deleter).execute(items)
        assertEquals(FailurePolicy.MAX_CONSECUTIVE_FAILURES, r.failedCount)
        assertEquals(0, r.deletedCount)
        assertTrue(r.stoppedEarly)
        assertTrue(r.stopReason.contains("连续"))
    }

    @Test
    fun successResetsConsecutiveFailureCounter() {
        val items = (0 until 12).map { l1("/sdcard/Android/data/com.x/cache/f$it.tmp") }
        val fail = setOf(items[0].path, items[1].path, items[3].path, items[4].path)
        val r = CleanExecutor(FakeDeleter(fail)).execute(items)
        assertEquals(8, r.deletedCount)
        assertEquals(4, r.failedCount)
        assertFalse("失败被成功打断，不应触发连续失败中止", r.stoppedEarly)
    }

    @Test
    fun reportOnlyL3AndUncheckedNeverEnterQueue() {
        val items = listOf(
            l1("/sdcard/Android/data/com.x/cache/a.tmp"),
            l1("/sdcard/Android/data/com.x/cache/b.tmp", checked = false),
            evaluator.toScanItem("/sdcard/Download/big.zip", 5L).copy(checked = true),
            evaluator.toScanItem("/data/data/com.x/databases/x.db", 5L).copy(checked = true)
        )
        val deleter = FakeDeleter()
        val r = CleanExecutor(deleter).execute(items)
        assertEquals(1, r.requested)
        assertEquals(1, r.deletedCount)
        assertEquals(1, deleter.deleted.size)
    }

    @Test
    fun deletionReverifiesPathGuardAndBlocksL3() {
        val legit = l1("/sdcard/Android/data/com.x/cache/ok.tmp")
        val tampered = legit.copy(path = "/data/data/com.x/databases/msg.db")
        val deleter = FakeDeleter()
        val r = CleanExecutor(deleter).execute(listOf(tampered))
        assertEquals(0, r.deletedCount)
        assertFalse("复核拒绝不应中止整批", r.stoppedEarly)
        assertEquals(1, r.guardRejectedCount)
        assertTrue(r.guardRejectReasons.any { it.contains("L3") })
        assertTrue(deleter.deleted.isEmpty())
    }

    @Test
    fun guardRejectionSkipsOnlyThatItemAndKeepsCleaning() {
        val ok1 = l1("/sdcard/Android/data/com.x/cache/a.tmp")
        val blocked = ok1.copy(path = "/data/data/com.x/databases/msg.db")
        val ok2 = l1("/sdcard/Android/data/com.x/cache/b.tmp")
        val deleter = FakeDeleter()
        val r = CleanExecutor(deleter).execute(listOf(ok1, blocked, ok2))
        assertEquals(3, r.requested)
        assertEquals(2, r.deletedCount)
        assertEquals(1, r.guardRejectedCount)
        assertEquals(0, r.failedCount)
        assertFalse(r.stoppedEarly)
        assertEquals(listOf(ok1.path, ok2.path), deleter.deleted)
    }

    @Test
    fun allItemsRejectedStillReportReasonsWithoutStopping() {
        val bad = l1("/sdcard/Android/data/com.x/cache/a.tmp")
            .copy(path = "/data/data/com.x/databases/msg.db")
        val r = CleanExecutor(FakeDeleter()).execute(
            listOf(bad, bad.copy(path = "/system/app/Foo.apk"))
        )
        assertEquals(0, r.deletedCount)
        assertFalse(r.stoppedEarly)
        assertEquals(2, r.guardRejectedCount)
        assertTrue(r.guardRejectReasons.isNotEmpty())
        assertTrue(r.guardRejectReasons.all { it.contains("L3") })
    }

    @Test
    fun structuralStorageDirectoryIsNeverDeleted() {
        // 真机回归：/sdcard/Android/data 被误判为空目录，删除必然失败并拖垮整批
        val structural = emptyDir("/sdcard/Android/data")
        val normal = emptyDir("/sdcard/Download/.old_empty_folder")
        val deleter = FakeDeleter()
        val r = CleanExecutor(deleter).execute(listOf(structural, normal))
        assertEquals(1, r.deletedCount)
        assertEquals(1, r.guardRejectedCount)
        assertFalse(r.stoppedEarly)
        assertTrue(r.guardRejectReasons.any { it.contains("结构目录") })
        assertEquals(listOf(normal.path), deleter.deleted)
    }

    @Test
    fun cancelStopsImmediately() {
        val items = (0 until 5).map { l1("/sdcard/Android/data/com.x/cache/f$it.tmp") }
        val deleter = FakeDeleter()
        val r = CleanExecutor(deleter).execute(items, isCancelled = { true })
        assertEquals(0, r.deletedCount)
        assertTrue(r.stoppedEarly)
    }

    @Test
    fun journalWritesJsonlWithoutFileContent() {
        val dir = Files.createTempDirectory("junkmaster-journal").toFile()
        dir.deleteOnExit()
        val journal = DeleteJournal(dir)
        val items = (0 until 3).map { l1("/sdcard/Android/data/com.x/cache/f$it.tmp") }
        val r = CleanExecutor(FakeDeleter(), journal = journal).execute(items)
        assertEquals(3, r.deletedCount)
        val files = journal.files()
        assertEquals(1, files.size)
        val text = files[0].readText()
        assertTrue(text.contains("\"path\""))
        assertTrue(text.contains("f0.tmp"))
        assertEquals(3, text.trim().lines().size)
        assertFalse("日志不得记录文件内容", text.contains("content"))
    }

    @Test
    fun capacityBeforeAfterAreReportedOnExternalStorage() {
        val items = listOf(l1("/sdcard/Android/data/com.x/cache/a.tmp", 100L))
        val capacity = FakeCapacity()
        val r = CleanExecutor(FakeDeleter(), capacityProbe = capacity).execute(items)
        assertEquals(10_000L, r.capacityBefore!!)
        assertEquals(10_000L, r.capacityAfter!!)
        assertEquals(0L, r.capacityDelta!!)
        assertEquals(100L, r.freedBytes)
        assertTrue("容量必须探测共享存储而非根分区", capacity.queried.all { it == StorageRoots.DEFAULT_STORAGE_PATH })
    }

    /** 第四轮真机回归：防误删的主动放弃绝不能被当成删除失败。 */
    private class SafeSkipDeleter(private val skipOn: Set<String>) : FileDeleter {
        val deleted = mutableListOf<String>()
        override fun delete(path: String, isDirectory: Boolean): DeleteOutcome =
            if (path in skipOn) {
                DeleteOutcome(false, "目录已非空，已跳过（防误删）", safeSkip = true)
            } else {
                deleted += path
                DeleteOutcome(true, "ok")
            }
    }

    @Test
    fun manyConsecutiveSafeSkipsNeverTriggerFailClosed() {
        // 真机现场：178 项全是空目录，前 12 项连续因竞态被防误删跳过，
        // 旧版会在第 5 项触发「系统性异常已中止」，队列只跑了 75 项。
        val items = (0 until 30).map { l1("/sdcard/Download/cache/f$it.tmp") }
        val skip = items.take(12).map { it.path }.toSet()
        val deleter = SafeSkipDeleter(skip)
        val r = CleanExecutor(deleter).execute(items)

        assertEquals(18, r.deletedCount)
        assertEquals(12, r.skippedCount)
        assertEquals(0, r.failedCount)
        assertFalse("安全跳过不得中止整批", r.stoppedEarly)
        assertEquals("", r.stopReason)
        assertTrue(r.skippedReasons.any { it.contains("非空") })
        assertTrue("跳过项的原因不能混进失败原因", r.failedReasons.isEmpty())
        assertEquals(18, deleter.deleted.size)
    }

    @Test
    fun safeSkipsDoNotHideASystemicHardFailureStreak() {
        val items = (0 until 10).map { l1("/sdcard/Download/cache/f$it.tmp") }
        val hardFail = items.take(4).map { it.path }.toSet()
        val skip = setOf(items[4].path, items[5].path, items[6].path)
        val deleter = object : FileDeleter {
            override fun delete(path: String, isDirectory: Boolean): DeleteOutcome = when {
                path in hardFail -> DeleteOutcome(false, "删除失败：AccessDeniedException")
                path in skip -> DeleteOutcome(false, "目录已非空，已跳过（防误删）", safeSkip = true)
                else -> DeleteOutcome(false, "删除失败：AccessDeniedException")
            }
        }
        val r = CleanExecutor(deleter).execute(items)
        assertTrue("连续 5 次硬失败仍必须 fail-closed 中止", r.stoppedEarly)
        assertEquals(5, r.failedCount)
        assertEquals(3, r.skippedCount)
        assertTrue(r.stopReason.contains("连续"))
    }

    @Test
    fun emptyDirDeletionsAreCountedSeparatelyToExplainZeroBytes() {
        val dirs = (0 until 3).map { emptyDir("/sdcard/Download/.old$it") }
        val r = CleanExecutor(FakeDeleter()).execute(dirs)
        assertEquals(3, r.deletedCount)
        assertEquals(3, r.emptyDirDeletedCount)
        assertEquals(0L, r.freedBytes)
        assertTrue("只清空目录时必须标记 onlyEmptyDirs", r.onlyEmptyDirs)
    }

    @Test
    fun combineKeepsSkipAndEmptyDirCounters() {
        val a = CleanReport(1, 1, 0L, 0, 1, false, "", null, null,
            skippedCount = 2, skippedReasons = listOf("S"), emptyDirDeletedCount = 1)
        val b = CleanReport(1, 1, 10L, 0, 1, false, "", null, null,
            skippedCount = 3, skippedReasons = listOf("S", "T"), emptyDirDeletedCount = 0)
        val c = a.combine(b)
        assertEquals(2, c.deletedCount)
        assertEquals(5, c.skippedCount)
        assertEquals(1, c.emptyDirDeletedCount)
        assertEquals(setOf("S", "T"), c.skippedReasons.toSet())
    }
}
