package io.github.junkmaster.app

import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.clean.PathGuard
import io.github.junkmaster.app.clean.combine
import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator
import io.github.junkmaster.app.scan.ItemSource
import io.github.junkmaster.app.scan.ScanSession
import io.github.junkmaster.app.scan.ScanSources
import io.github.junkmaster.app.shizuku.BindAction
import io.github.junkmaster.app.shizuku.ShizukuBindPolicy
import io.github.junkmaster.app.shizuku.ShizukuStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 老板补充的产品主开关回归测试：
 * 不开 Shizuku = 基础安全清扫可用；开 Shizuku = 解锁类似 SD Maid 的深度清理权限。
 */
class DualModeTest {

    private fun source(name: String): ItemSource = object : ItemSource {
        override val name: String = name
        override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) = Unit
    }

    @Test
    fun `base sources are always available without shizuku`() {
        val base = listOf(source("temp"), source("thumbnail"))
        val result = ScanSources.build(base, source("deep"), shizukuReady = false)
        assertEquals(2, result.size)
        assertEquals(listOf("temp", "thumbnail"), result.map { it.name })
    }

    @Test
    fun `privileged source is added only when shizuku is ready`() {
        val base = listOf(source("temp"))
        val deep = source("deep")
        assertEquals(listOf("temp", "deep"), ScanSources.build(base, deep, true).map { it.name })
        assertEquals(listOf("temp"), ScanSources.build(base, null, true).map { it.name })
    }

    @Test
    fun `privileged cache item is deletable L1 when shizuku ready`() {
        val evaluator = RuleEvaluator(shizukuAvailable = { true })
        val item = evaluator.toScanItem(
            "/sdcard/Android/data/com.x/cache/a.tmp",
            10L,
            preferCategory = ScanCategory.APP_CACHE,
            requiresShizuku = true
        )
        assertEquals(DeleteMode.DIRECT_DELETE, item.deleteMode)
        assertTrue(item.requiresShizuku)
        assertTrue(item.checked)
        assertTrue(item.isSafe)
    }

    @Test
    fun `privileged item degrades to report only when shizuku lost`() {
        val evaluator = RuleEvaluator(shizukuAvailable = { false })
        val item = evaluator.toScanItem(
            "/sdcard/Android/data/com.x/code_cache/o.dex",
            20L,
            preferCategory = ScanCategory.CODE_CACHE,
            requiresShizuku = true
        )
        assertEquals(DeleteMode.REPORT_ONLY, item.deleteMode)
        assertFalse(item.checked)
        assertTrue(item.reason.contains("Shizuku"))
    }

    @Test
    fun `base L1 item never requires shizuku`() {
        val item = RuleEvaluator().toScanItem(
            "/sdcard/Download/scratch.tmp",
            5L,
            preferCategory = ScanCategory.TEMP_FILE
        )
        assertFalse(item.requiresShizuku)
        assertEquals(DeleteMode.DIRECT_DELETE, item.deleteMode)
    }

    @Test
    fun `combine merges base and deep clean reports`() {
        val base = CleanReport(1, 1, 10L, 0, 1, false, "", 100L, 110L)
        val deep = CleanReport(2, 2, 20L, 0, 1, false, "", 110L, 140L)
        val merged = base.combine(deep)
        assertEquals(3, merged.requested)
        assertEquals(3, merged.deletedCount)
        assertEquals(30L, merged.freedBytes)
        assertEquals(100L, merged.capacityBefore)
        assertEquals(140L, merged.capacityAfter)
        assertEquals(40L, merged.capacityDelta)
    }

    @Test
    fun `combine merges guard rejection counts and dedupes reasons`() {
        val base = CleanReport(
            2, 1, 10L, 0, 1, false, "", 100L, 110L,
            guardRejectedCount = 1, guardRejectReasons = listOf("L3 禁止")
        )
        val deep = CleanReport(
            3, 2, 20L, 0, 1, false, "", 110L, 140L,
            guardRejectedCount = 1, guardRejectReasons = listOf("L3 禁止", "用户白名单")
        )
        val merged = base.combine(deep)
        assertEquals(2, merged.guardRejectedCount)
        assertEquals(listOf("L3 禁止", "用户白名单"), merged.guardRejectReasons)
    }

    @Test
    fun `android data cache is allowed but app private data is blocked`() {
        assertTrue(PathGuard.verify("/sdcard/Android/data/com.x/cache/a.tmp").allowed)
        assertFalse(PathGuard.verify("/data/data/com.x/databases/msg.db").allowed)
        assertFalse(PathGuard.verify("/sdcard/DCIM/Camera/IMG_001.jpg").allowed)
    }

    @Test
    fun `cold start with permission granted but bridge not connected keeps deep source out`() {
        val base = listOf(source("temp"))
        val deep = source("deep")

        val coldStart = ShizukuBindPolicy.decide(
            shizukuAlive = true,
            permissionGranted = true,
            bridgeAlive = false
        )
        assertEquals(ShizukuStatus.NOT_RUNNING, coldStart.status)
        assertEquals(BindAction.AUTO_BIND, coldStart.action)
        assertFalse("bind 异步窗口期不得把深度源加入扫描", coldStart.status.operational)
        assertEquals(
            listOf("temp"),
            ScanSources.build(base, deep, coldStart.status == ShizukuStatus.READY).map { it.name }
        )

        val connected = ShizukuBindPolicy.decide(
            shizukuAlive = true,
            permissionGranted = true,
            bridgeAlive = true
        )
        assertEquals(ShizukuStatus.READY, connected.status)
        assertEquals(
            listOf("temp", "deep"),
            ScanSources.build(base, deep, connected.status == ShizukuStatus.READY).map { it.name }
        )
    }
}
