package io.github.junkmaster.app

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.scan.BasicCacheSource
import io.github.junkmaster.app.scan.FileEntry
import io.github.junkmaster.app.scan.ScanSession
import io.github.junkmaster.app.scan.TempFileFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第四轮真机回归：基础模式不能再只扫出空目录（0 B）。
 * 同时验证「零误删」边界：用户文档、媒体、应用私有数据区、L3 媒体目录一律不默认勾删。
 */
class BasicCacheSourceTest {

    private fun entry(path: String, size: Long = 1024L, isDir: Boolean = false) = FileEntry(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = isDir,
        sizeBytes = size,
        lastModifiedMs = 1L
    )

    private fun collect(vararg entries: FileEntry): List<ScanItem> {
        val out = mutableListOf<ScanItem>()
        BasicCacheSource(provider = { entries.toList() })
            .collect(ScanSession(), { out += it })
        return out
    }

    @Test
    fun cacheDirPlusRegenerableSuffixIsL1Deletable() {
        val items = collect(entry("/sdcard/MyApp/cache/download.tmp"))
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals(RiskLevel.L1_SAFE, item.riskLevel)
        assertEquals(DeleteMode.DIRECT_DELETE, item.deleteMode)
        assertTrue("L1 应默认勾选", item.checked)
        assertEquals("l1.temp_file", item.ruleId)
        assertEquals(1024L, item.sizeBytes)
    }

    @Test
    fun userDocumentsAndMediaInCacheDirAreNotEmitted() {
        val items = collect(
            entry("/sdcard/MyApp/cache/important.docx"),
            entry("/sdcard/MyApp/cache/photo.jpg"),
            entry("/sdcard/MyApp/cache/voice.amr"),
            entry("/sdcard/MyApp/cache/notes.db")
        )
        assertTrue("用户文档 / 媒体 / 数据库绝不能默认清", items.isEmpty())
    }

    @Test
    fun androidDataObbMediaAreLeftToDeepMode() {
        val items = collect(
            entry("/sdcard/Android/data/com.x/cache/a.tmp"),
            entry("/sdcard/Android/obb/com.x/cache/a.tmp"),
            entry("/sdcard/Android/media/com.x/cache/a.tmp")
        )
        assertTrue("深度区必须由 Shizuku / L2 处理，基础模式不越界", items.isEmpty())
    }

    @Test
    fun l3MediaPathsAreClassifiedForbiddenEvenIfSuffixMatches() {
        val items = collect(
            entry("/sdcard/DCIM/.thumbnails/x.thumb"),
            entry("/sdcard/Pictures/.cache/x.tmp"),
            entry("/sdcard/MyApp/databases/x.tmp")
        )
        assertTrue(items.isNotEmpty())
        assertTrue(
            "L3 路径必须判为禁止且不可勾选",
            items.all { it.riskLevel == RiskLevel.L3_FORBIDDEN && !it.checked && !it.deleteMode.canDelete }
        )
    }

    @Test
    fun directoriesAndZeroByteFilesAreIgnored() {
        val items = collect(
            entry("/sdcard/MyApp/cache", isDir = true),
            entry("/sdcard/MyApp/cache/placeholder.tmp", size = 0L)
        )
        assertTrue(items.isEmpty())
    }

    @Test
    fun fileOutsideCacheDirIsNotEmitted() {
        // 这类交给 TempFileSource（只按后缀扫），BasicCacheSource 不重复产出
        val items = collect(entry("/sdcard/Download/report.tmp"))
        assertTrue(items.isEmpty())
    }

    @Test
    fun tempSuffixListCoversDownloadFragments() {
        assertTrue(TempFileFilter.isTemp("video.mp4.crdownload"))
        assertTrue(TempFileFilter.isTemp("update.part"))
        assertTrue(TempFileFilter.isTemp("blob.dctmp"))
        assertTrue(TempFileFilter.isTemp("backup.tmp~"))
        assertFalse(TempFileFilter.isTemp("photo.jpg"))
        assertFalse(TempFileFilter.isTemp("app.apk"))
    }
}
