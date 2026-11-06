package io.github.junkmaster.app.scan

import java.io.File

/**
 * 文件 / 目录的**实时**只读探测结果（第七轮反馈第 3 条）。
 *
 * 扫描结果是快照，用户点开某一项时可能已经变了（被别的应用删了、目录里又写了文件）。
 * 所以点「查看路径」时不复用扫描结果，而是当场再探一次，把「现在到底还在不在、
 * 是不是空目录」如实告诉用户 —— 这也顺带解释了上一轮「扫描结果显示空目录、
 * 删除却报非空」的困惑。
 *
 * 纯 JVM，不碰 android.*，可用假实现做单测。
 */
data class PathProbe(
    val exists: Boolean,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
    /** 目录的直接子项数；-1 = 不可列举（无权限 / 受保护的骨架目录） */
    val childCount: Int,
    val readable: Boolean
) {
    /** 现在是空目录（且确实列举过，不是「列举失败」被当成空）。 */
    val emptyDirectory: Boolean get() = exists && isDirectory && readable && childCount == 0

    companion object {
        val MISSING: PathProbe = PathProbe(false, false, 0L, 0L, -1, false)
    }
}

/** 便于单测注入：真实实现读文件系统，测试实现返回构造好的快照。 */
interface PathProbeSource {
    fun probe(path: String): PathProbe
}

/** 真实实现：只读 stat / list，绝不做任何删除或写入。 */
object FileSystemPathProbe : PathProbeSource {

    override fun probe(path: String): PathProbe {
        val f = File(path)
        val exists = try {
            f.exists()
        } catch (t: Throwable) {
            false
        }
        if (!exists) return PathProbe.MISSING
        val isDir = try {
            f.isDirectory
        } catch (t: Throwable) {
            false
        }
        val size = if (isDir) {
            0L
        } else {
            try {
                f.length()
            } catch (t: Throwable) {
                0L
            }
        }
        val mtime = try {
            f.lastModified()
        } catch (t: Throwable) {
            0L
        }
        val children = if (isDir) {
            try {
                f.list()?.size ?: -1
            } catch (t: Throwable) {
                -1
            }
        } else {
            -1
        }
        return PathProbe(
            exists = true,
            isDirectory = isDir,
            sizeBytes = size,
            lastModifiedMs = mtime,
            childCount = children,
            readable = !isDir || children >= 0
        )
    }
}
