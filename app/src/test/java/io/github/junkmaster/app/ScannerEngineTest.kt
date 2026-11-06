package io.github.junkmaster.app

import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator
import io.github.junkmaster.app.scan.ItemSource
import io.github.junkmaster.app.scan.ScanSession
import io.github.junkmaster.app.scan.ScannerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：dry-run 只读收集、取消、进度、单来源失败不阻断 */
class ScannerEngineTest {

    private val evaluator = RuleEvaluator()
    private val sample = evaluator.toScanItem("/sdcard/Android/data/com.x/cache/a.tmp", 10L)

    private fun source(
        label: String,
        items: List<ScanItem>,
        throwError: Boolean = false
    ): ItemSource = object : ItemSource {
        override val name: String = label
        override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
            if (throwError) throw RuntimeException("source failed")
            items.forEach { if (!session.cancelled) emit(it) }
        }
    }

    @Test
    fun dryRunCollectsItemsIntoL1() {
        val engine = ScannerEngine(listOf(source("s1", listOf(sample))))
        val r = engine.scan()
        assertEquals(1, r.totalScanned)
        assertEquals(1, r.l1Items.size)
    }

    @Test
    fun cancelBeforeScanReturnsCancelledEmptyResult() {
        val session = ScanSession().apply { cancel() }
        val r = ScannerEngine(listOf(source("s", listOf(sample)))).scan(session)
        assertTrue(r.cancelled)
        assertEquals(0, r.totalScanned)
    }

    @Test
    fun progressReachesHundredPercent() {
        var last = -1
        val engine = ScannerEngine(listOf(source("a", emptyList()), source("b", emptyList())))
        engine.scan(onProgress = { last = it.percent })
        assertEquals(100, last)
    }

    @Test
    fun failingSourceDoesNotBlockOthers() {
        val engine = ScannerEngine(
            listOf(source("bad", emptyList(), throwError = true), source("good", listOf(sample)))
        )
        val r = engine.scan()
        assertEquals(1, r.totalScanned)
        assertEquals(1, r.l1Items.size)
    }
}
