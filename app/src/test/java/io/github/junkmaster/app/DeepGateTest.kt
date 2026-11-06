package io.github.junkmaster.app

import io.github.junkmaster.app.scan.DeepDenyReason
import io.github.junkmaster.app.scan.DeepGate
import io.github.junkmaster.app.shizuku.BindResult
import io.github.junkmaster.app.shizuku.ShizukuStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「进入深度确认」放行判定（第六轮反馈第 5 条）。
 *
 * 原实现非 ok 时 UI 什么都不做，用户点按钮零反馈；
 * 这里保证每种不可用状态都能给出**可区分的原因**与可直接展示的补充说明。
 */
class DeepGateTest {

    @Test
    fun readyAllowsEntry() {
        val r = DeepGate.decide(3, ShizukuStatus.READY, BindResult.ok("已连接"))
        assertTrue(r.ok)
        assertEquals(DeepDenyReason.OK, r.reason)
    }

    @Test
    fun emptySelectionIsRejectedFirst() {
        // 即使 Shizuku 已就绪，没有勾选也不该进入确认页
        val r = DeepGate.decide(0, ShizukuStatus.READY)
        assertFalse(r.ok)
        assertEquals(DeepDenyReason.NO_SELECTION, r.reason)
    }

    @Test
    fun serviceNotRunningIsDistinguishedFromBinding() {
        val r = DeepGate.decide(2, ShizukuStatus.NOT_INSTALLED)
        assertEquals(DeepDenyReason.SERVICE_NOT_RUNNING, r.reason)
    }

    @Test
    fun permissionStatesAreDistinct() {
        assertEquals(
            DeepDenyReason.NEED_PERMISSION,
            DeepGate.decide(1, ShizukuStatus.NEED_PERMISSION).reason
        )
        assertEquals(
            DeepDenyReason.DENIED,
            DeepGate.decide(1, ShizukuStatus.DENIED).reason
        )
        assertEquals(
            DeepDenyReason.LOST,
            DeepGate.decide(1, ShizukuStatus.LOST).reason
        )
    }

    @Test
    fun inFlightBindIsNotReportedAsFailure() {
        val r = DeepGate.decide(1, ShizukuStatus.NOT_RUNNING, BindResult.ok("已发起绑定，等待 UserService 连接"))
        assertEquals(DeepDenyReason.BINDING, r.reason)
        assertTrue("正在绑定时要带上补充说明", r.detail.contains("等待"))
    }

    @Test
    fun failedBindCarriesOriginalReason() {
        val r = DeepGate.decide(
            1,
            ShizukuStatus.NOT_RUNNING,
            BindResult.fail("Shizuku 服务未运行：请先在 Shizuku 里启动服务")
        )
        assertEquals(DeepDenyReason.BIND_FAILED, r.reason)
        assertTrue("失败原因必须原样带给用户", r.detail.contains("启动服务"))
    }

    @Test
    fun porterModeFallsBackToBindFailed() {
        val r = DeepGate.decide(1, ShizukuStatus.PORTER)
        assertFalse(r.ok)
        assertEquals(DeepDenyReason.BIND_FAILED, r.reason)
        assertTrue(r.detail.isNotBlank())
    }
}
