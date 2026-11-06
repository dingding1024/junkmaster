package io.github.junkmaster.app

import io.github.junkmaster.app.shizuku.BindResult
import io.github.junkmaster.app.shizuku.BindTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 绑定链路留痕（第六轮反馈第 5 条）。
 *
 * 真机上「Shizuku 已授权、binder 能 ping，但状态恒为服务未运行」时，
 * 用户手上没有 adb，只能靠这份 trace 判断卡在哪一步，因此格式与截断行为必须稳定。
 */
class BindTraceTest {

    @Before
    fun reset() {
        BindTrace.reset()
    }

    @Test
    fun recordsSequenceAndKeepsOrder() {
        BindTrace.record("pingBinder", true, "alive")
        BindTrace.record("checkSelfPermission", true, "granted")
        BindTrace.record("bindUserService", true, "tag=junkmaster-cleaner")

        val all = BindTrace.snapshot()
        assertEquals(3, all.size)
        assertEquals(listOf(1, 2, 3), all.map { it.seq })
        assertEquals("pingBinder", all.first().step)
        assertEquals("bindUserService", all.last().step)
        assertTrue(all.all { it.ok })
    }

    @Test
    fun keepsOnlyLastEntriesWhenOverflow() {
        repeat(BindTrace.MAX_ENTRIES + 12) { i ->
            BindTrace.record("step$i", true)
        }
        val all = BindTrace.snapshot()
        assertEquals(BindTrace.MAX_ENTRIES, all.size)
        // 最早的 12 条被环形覆盖，最后一条一定是最新写入
        assertEquals("step" + (BindTrace.MAX_ENTRIES + 11), all.last().step)
    }

    @Test
    fun lastFailurePointsToFirstBrokenStep() {
        assertNull("没有失败时不应返回失败项", BindTrace.lastFailure())
        BindTrace.record("pingBinder", true, "alive")
        BindTrace.record("bindUserService", false, "IllegalStateException: not granted")
        BindTrace.record("probe", true, "uid=2000")

        val fail = BindTrace.lastFailure()
        assertEquals("bindUserService", fail?.step)
        assertTrue(fail?.detail?.contains("IllegalStateException") == true)
        assertTrue(BindTrace.summary().contains("bindUserService"))
    }

    @Test
    fun renderIsSingleLineAndTruncatesDetail() {
        val long = "x".repeat(500)
        BindTrace.record("bindUserService", false, "boom\nstack" + long)
        val text = BindTrace.render()
        assertTrue("详情必须压成一行，避免报告错行", text.contains("FAIL"))
        assertFalse(text.contains("\n   at "))
        assertEquals("每一行一条记录", 1, text.lines().size)
        assertTrue("详情必须截断", text.length < 400)
    }

    @Test
    fun renderOnEmptyShowsPlaceholder() {
        assertTrue(BindTrace.render().contains("无绑定记录"))
    }

    @Test
    fun bindResultCarriesReason() {
        val ok = BindResult.ok("已发起绑定")
        val fail = BindResult.fail("Shizuku 服务未运行")
        assertTrue(ok.started)
        assertFalse(fail.started)
        assertEquals("Shizuku 服务未运行", fail.reason)
    }
}
