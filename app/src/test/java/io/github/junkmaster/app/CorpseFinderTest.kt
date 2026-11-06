package io.github.junkmaster.app

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.scan.CorpseFinder
import io.github.junkmaster.app.scan.FileEntry
import io.github.junkmaster.app.scan.ScanSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：CorpseFinder「应用可能回来」、30 天门槛、只读收集 */
class CorpseFinderTest {

    private val now = 2_000_000_000_000L
    private val old = now - CorpseFinder.MIN_AGE_MS - 1_000L

    @Test
    fun installedAppIsNotCorpseEvenIfVeryOld() {
        // 应用仍安装 → 可能回来，绝不判为残留
        assertFalse(CorpseFinder.isCorpse("com.example", setOf("com.example"), old, now))
    }

    @Test
    fun uninstalledOldDirectoryIsCorpse() {
        assertTrue(CorpseFinder.isCorpse("com.example", emptySet(), old, now))
    }

    @Test
    fun freshUninstalledDirectoryIsNotCorpse() {
        assertFalse(CorpseFinder.isCorpse("com.example", emptySet(), now - 1_000L, now))
    }

    @Test
    fun nullOwnerIsNotCorpse() {
        assertFalse(CorpseFinder.isCorpse(null, emptySet(), old, now))
    }

    @Test
    fun collectEmitsOnlyTrueResidue() {
        val entries = listOf(
            FileEntry("/sdcard/Android/data/com.gone/cache", "cache", true, 0L, old, "com.gone"),
            FileEntry("/sdcard/Android/data/com.here/cache", "cache", true, 0L, old, "com.here")
        )
        val finder = CorpseFinder({ entries }, { setOf("com.here") }, nowMs = now)
        val out = mutableListOf<ScanItem>()
        finder.collect(ScanSession()) { out += it }
        assertEquals(1, out.size)
        assertEquals(ScanCategory.CORPSE, out[0].category)
        assertEquals("cons.corpse", out[0].consequenceId)
        assertFalse(out[0].checked) // L2 默认全不勾
    }

    @Test
    fun cancelledSessionEmitsNothing() {
        val entries = listOf(
            FileEntry("/sdcard/Android/data/com.gone/cache", "cache", true, 0L, old, "com.gone")
        )
        val finder = CorpseFinder({ entries }, { emptySet() }, nowMs = now)
        val session = ScanSession().apply { cancel() }
        val out = mutableListOf<ScanItem>()
        finder.collect(session) { out += it }
        assertTrue(out.isEmpty())
    }
}
