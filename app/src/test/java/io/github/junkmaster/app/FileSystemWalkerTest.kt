package io.github.junkmaster.app

import io.github.junkmaster.app.scan.FileSystemWalker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 遍历器必须把「目录可列举」如实记进 FileEntry.readable，供 EmptyDirFinder 排除误判。 */
class FileSystemWalkerTest {

    @Test
    fun realTreeMarksDirectoriesReadable() {
        val root = Files.createTempDirectory("jm-walk").toFile()
        File(root, "empty").mkdirs()
        File(root, "nested").mkdirs()
        File(root, "nested/a.tmp").writeText("x")

        val entries = FileSystemWalker().walk(root.absolutePath)
        assertEquals(3, entries.size)
        assertTrue(entries.all { it.readable })
        assertEquals(2, entries.count { it.isDirectory })
        assertEquals(1, entries.count { !it.isDirectory })
        assertTrue(entries.any { it.name == "a.tmp" })
        root.deleteRecursively()
    }

    @Test
    fun nonExistentRootReturnsEmpty() {
        val root = Files.createTempDirectory("jm-walk-missing").toFile()
        assertTrue(FileSystemWalker().walk(File(root, "nope").absolutePath).isEmpty())
        root.deleteRecursively()
    }
}
