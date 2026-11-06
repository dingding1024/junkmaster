package io.github.junkmaster.app

import io.github.junkmaster.app.scan.EmptyDirFinder
import io.github.junkmaster.app.scan.FileEntry
import io.github.junkmaster.app.scan.ScanSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 真机第三轮回归：/sdcard/Android/data 这类「列举不了」的目录曾被当成空目录并默认勾选，
 * 删除必然失败并把整批拖成 0 删除。这里用快照直接模拟该设备语义。
 */
class EmptyDirFinderTest {

    private fun entry(
        path: String,
        isDir: Boolean = true,
        readable: Boolean = true
    ) = FileEntry(path, path.substringAfterLast('/'), isDir, 0L, 0L, null, readable)

    private fun collect(vararg entries: FileEntry) =
        buildList {
            EmptyDirFinder(provider = { entries.toList() })
                .collect(ScanSession()) { add(it) }
        }

    @Test
    fun unreadableDirectoryIsNeverTreatedAsEmpty() {
        // listFiles() 返回 null（无权限 / 受保护），不是空目录
        val out = collect(entry("/sdcard/Download/UnreadableFolder", readable = false))
        assertTrue("不可列举的目录绝不能进入 L1 删除队列", out.isEmpty())
    }

    @Test
    fun structuralStorageDirectoriesAreExcluded() {
        val out = collect(
            entry("/sdcard"),
            entry("/sdcard/Android"),
            entry("/sdcard/Android/data"),
            entry("/sdcard/Android/obb"),
            entry("/sdcard/DCIM")
        )
        assertTrue("共享存储骨架目录绝不能作为空目录被删", out.isEmpty())
    }

    @Test
    fun genuineEmptyDirectoryIsEmittedAsCheckedL1() {
        val out = collect(entry("/sdcard/Download/OldEmptyFolder"))
        assertEquals(1, out.size)
        assertEquals("l1.empty_dir", out[0].ruleId)
        assertTrue(out[0].checked)
        assertFalse(out[0].requiresShizuku)
    }

    @Test
    fun directoryWithChildrenIsNotEmitted() {
        val out = collect(
            entry("/sdcard/Download/NotEmpty"),
            entry("/sdcard/Download/NotEmpty/keep.tmp", isDir = false)
        )
        assertTrue(out.isEmpty())
    }

    @Test
    fun mixedSnapshotOnlyEmitsRealEmptyDirs() {
        val out = collect(
            entry("/sdcard/Android/data", readable = false),
            entry("/sdcard/Download/NotEmpty"),
            entry("/sdcard/Download/NotEmpty/keep.tmp", isDir = false),
            entry("/sdcard/Download/TrulyEmpty")
        )
        assertEquals(1, out.size)
        assertEquals("/sdcard/Download/TrulyEmpty", out[0].path)
    }
}
