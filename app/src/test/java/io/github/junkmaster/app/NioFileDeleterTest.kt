package io.github.junkmaster.app

import io.github.junkmaster.app.clean.NioFileDeleter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 真机第三轮回归：目录删除必须「可列举 + 确实为空」，绝不递归删除，防止扫描后写入的文件被误删。 */
class NioFileDeleterTest {

    private val deleter = NioFileDeleter()

    @Test
    fun emptyDirectoryIsDeleted() {
        val root = Files.createTempDirectory("jm-del-empty").toFile()
        val empty = File(root, "empty").apply { mkdirs() }
        val r = deleter.delete(empty.absolutePath, true)
        assertTrue(r.message, r.success)
        assertFalse(empty.exists())
        root.deleteRecursively()
    }

    @Test
    fun nonEmptyDirectoryIsNotDeletedAndChildIsPreserved() {
        val root = Files.createTempDirectory("jm-del-nonempty").toFile()
        val dir = File(root, "dir").apply { mkdirs() }
        val child = File(dir, "keep.tmp").apply { writeText("do not delete") }
        val r = deleter.delete(dir.absolutePath, true)
        assertFalse(r.success)
        assertTrue("防误删放弃必须标记为安全跳过，而不是删除失败", r.safeSkip)
        assertTrue(r.message.contains("非空"))
        assertTrue("目录内的文件必须原样保留", child.exists())
        assertEquals("do not delete", child.readText())
        root.deleteRecursively()
    }

    @Test
    fun missingTargetCountsAsAlreadyGone() {
        val root = Files.createTempDirectory("jm-del-missing").toFile()
        val gone = File(root, "gone.tmp")
        val r = deleter.delete(gone.absolutePath, false)
        assertTrue(r.message, r.success)
        root.deleteRecursively()
    }

    @Test
    fun regularFileIsDeleted() {
        val root = Files.createTempDirectory("jm-del-file").toFile()
        val f = File(root, "a.tmp").apply { writeText("x") }
        val r = deleter.delete(f.absolutePath, false)
        assertTrue(r.message, r.success)
        assertFalse(f.exists())
        root.deleteRecursively()
    }

    @Test
    fun emptyDirectoryIsNotDeletedWhenItGainedAFile() {
        // 模拟「扫描时为空、删除前又有文件写入」的竞态：必须放弃删除而不是递归清空
        val root = Files.createTempDirectory("jm-del-race").toFile()
        val dir = File(root, "dir").apply { mkdirs() }
        File(dir, "new.tmp").writeText("x")
        val r = deleter.delete(dir.absolutePath, true)
        assertFalse(r.success)
        assertTrue("扫描/删除竞态属于安全跳过", r.safeSkip)
        assertTrue(File(dir, "new.tmp").exists())
        root.deleteRecursively()
    }
}
