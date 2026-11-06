package io.github.junkmaster.app.scan

import java.io.File
import java.nio.file.Files

/** 遍历产出的文件条目（纯 JVM，便于用假数据做单测） */
data class FileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    /** 卸载残留判定用的「归属包名」提示，可为空 */
    val ownerPackage: String? = null,
    /**
     * 目录是否成功列举过子项。false = listFiles() 返回 null（无权限 / 受保护的骨架目录），
     * **绝不能据此判定为空目录**：真机上 /sdcard/Android/data 就是这样被误判并进入删除队列的。
     */
    val readable: Boolean = true
)

typealias EntriesProvider = () -> List<FileEntry>

/**
 * 只读文件遍历器（M3）。纯 JVM，绝不删除任何文件。
 * 默认不跟随符号链接；单层异常（无权限 / 不存在）直接跳过。
 *
 * 2026-11-05 修正：目录的可列举性单独记录在 FileEntry.readable，遍历失败不再等同于「空目录」。
 */
class FileSystemWalker(private val followSymlinks: Boolean = false) {

    fun walk(root: String, maxDepth: Int = 6): List<FileEntry> {
        val out = mutableListOf<FileEntry>()
        val rootFile = File(root)
        if (!rootFile.exists()) return out
        val top = try {
            rootFile.listFiles()
        } catch (t: Throwable) {
            null
        } ?: return out
        walkInto(top, 0, maxDepth, out)
        return out
    }

    private fun walkInto(
        children: Array<File>,
        depth: Int,
        maxDepth: Int,
        out: MutableList<FileEntry>
    ) {
        if (depth > maxDepth) return
        for (f in children) {
            if (!followSymlinks && isSymlink(f)) continue
            val isDir = try { f.isDirectory } catch (t: Throwable) { false }
            // 目录只列举一次：既用于递归，也用于判定「是否真的能读」。
            val sub = if (isDir) {
                try { f.listFiles() } catch (t: Throwable) { null }
            } else {
                null
            }
            val size = if (isDir) 0L else try { f.length() } catch (t: Throwable) { 0L }
            out += FileEntry(
                path = f.absolutePath.replace('\\', '/'),
                name = f.name,
                isDirectory = isDir,
                sizeBytes = size,
                lastModifiedMs = try { f.lastModified() } catch (t: Throwable) { 0L },
                readable = !isDir || sub != null
            )
            if (isDir && sub != null) walkInto(sub, depth + 1, maxDepth, out)
        }
    }

    private fun isSymlink(f: File): Boolean = try {
        Files.isSymbolicLink(f.toPath())
    } catch (t: Throwable) {
        false
    }
}
