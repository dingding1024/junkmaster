package io.github.junkmaster.app

import io.github.junkmaster.app.shizuku.BindAction
import io.github.junkmaster.app.shizuku.ShizukuBindPolicy
import io.github.junkmaster.app.shizuku.ShizukuStateMachine
import io.github.junkmaster.app.shizuku.ShizukuStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：Shizuku 掉线 fail-closed（READY 是唯一放行态）+ 绑定决策四分支。 */
class ShizukuStateMachineTest {

    @Test
    fun initialIsNotInstalledAndNotOperational() {
        val m = ShizukuStateMachine()
        assertEquals(ShizukuStatus.NOT_INSTALLED, m.status)
        assertFalse(m.canDeepClean())
    }

    @Test
    fun readyAllowsDeepClean() {
        val m = ShizukuStateMachine()
        m.transition(ShizukuStatus.READY)
        assertTrue(m.canDeepClean())
    }

    @Test
    fun binderDeadFailsClosedImmediately() {
        val m = ShizukuStateMachine()
        m.transition(ShizukuStatus.READY)
        assertTrue(m.canDeepClean())
        m.onBinderDead()
        assertEquals(ShizukuStatus.LOST, m.status)
        assertFalse("掉线后必须立即中止深度操作", m.canDeepClean())
    }

    @Test
    fun permissionGrantedDoesNotFakeReadyBeforeBridgeConnected() {
        val m = ShizukuStateMachine()
        m.transition(ShizukuStatus.NEED_PERMISSION)
        m.onPermissionGranted()
        assertEquals(ShizukuStatus.NOT_RUNNING, m.status)
        assertFalse("授权成功不等于 UserService 已连接，不能自称 READY", m.canDeepClean())
    }

    @Test
    fun permissionGrantedAfterLostDoesNotFakeReady() {
        val m = ShizukuStateMachine()
        m.transition(ShizukuStatus.READY)
        m.onBinderDead()
        assertEquals(ShizukuStatus.LOST, m.status)
        m.onPermissionGranted()
        assertEquals(ShizukuStatus.NOT_RUNNING, m.status)
        assertFalse(m.canDeepClean())
    }

    @Test
    fun deniedAndPorterNeverOperational() {
        val m = ShizukuStateMachine()
        m.onPermissionDenied()
        assertFalse(m.canDeepClean())
        m.transition(ShizukuStatus.PORTER)
        assertFalse(m.canDeepClean())
        m.transition(ShizukuStatus.DENIED)
        assertFalse(m.canDeepClean())
    }

    @Test
    fun onlyReadyIsOperational() {
        listOf(
            ShizukuStatus.NOT_INSTALLED,
            ShizukuStatus.NOT_RUNNING,
            ShizukuStatus.NEED_PERMISSION,
            ShizukuStatus.LOST,
            ShizukuStatus.DENIED,
            ShizukuStatus.PORTER
        ).forEach { status ->
            assertFalse("$status 不应放行深度操作", status.operational)
        }
        assertTrue(ShizukuStatus.READY.operational)
    }

    @Test
    fun policyWaitsWhenShizukuNotRunning() {
        val d = ShizukuBindPolicy.decide(
            shizukuAlive = false, permissionGranted = true, bridgeAlive = false
        )
        assertEquals(ShizukuStatus.NOT_RUNNING, d.status)
        assertEquals(BindAction.WAIT_SHIZUKU, d.action)
        assertFalse(d.status.operational)
    }

    @Test
    fun policyWaitsForPermission() {
        val d = ShizukuBindPolicy.decide(
            shizukuAlive = true, permissionGranted = false, bridgeAlive = false
        )
        assertEquals(ShizukuStatus.NEED_PERMISSION, d.status)
        assertEquals(BindAction.WAIT_PERMISSION, d.action)
        assertFalse(d.status.operational)
    }

    @Test
    fun policyAutoBindsOnColdStartWhenPermissionAlreadyGranted() {
        // 用户在 Shizuku Manager 里早就授权过，App 冷启动 bridge 为空：应自动补绑定
        val d = ShizukuBindPolicy.decide(
            shizukuAlive = true, permissionGranted = true, bridgeAlive = false
        )
        assertEquals(ShizukuStatus.NOT_RUNNING, d.status)
        assertEquals(BindAction.AUTO_BIND, d.action)
        assertFalse("自动绑定期间仍不得放行深度操作", d.status.operational)
    }

    @Test
    fun policyReportsReadyOnlyWithLiveBridge() {
        val d = ShizukuBindPolicy.decide(
            shizukuAlive = true, permissionGranted = true, bridgeAlive = true
        )
        assertEquals(ShizukuStatus.READY, d.status)
        assertEquals(BindAction.NONE, d.action)
        assertTrue(d.status.operational)
    }

    @Test
    fun policyAutoBindsOnRecoveryAfterBinderDeath() {
        // 掉线恢复：Shizuku 重启后刷新，bridge 已死 -> 重新绑定，但恢复前不可放行
        val d = ShizukuBindPolicy.decide(
            shizukuAlive = true, permissionGranted = true, bridgeAlive = false
        )
        assertEquals(BindAction.AUTO_BIND, d.action)
        assertFalse(d.status.operational)
        val m = ShizukuStateMachine()
        m.onBinderDead()
        m.transition(d.status)
        assertFalse("恢复期间必须保持 fail-closed", m.canDeepClean())
    }

    @Test
    fun policyNeverReportsReadyWithoutPermissionEvenIfBridgeAlive() {
        val d = ShizukuBindPolicy.decide(
            shizukuAlive = true, permissionGranted = false, bridgeAlive = true
        )
        assertEquals(ShizukuStatus.NEED_PERMISSION, d.status)
        assertFalse(d.status.operational)
    }
}
