package io.github.junkmaster.app.diag

import android.content.Context
import android.os.Environment
import io.github.junkmaster.app.clean.PathGuard
import io.github.junkmaster.app.clean.StorageRoots
import java.io.File
import java.nio.file.Files
import java.util.UUID


/**
 * 文件系统 / PathGuard 实测探针（只在用户点「一键生成报告」时运行）。
 *
 * 目的：老板没有 adb，纯 JVM 单测又无法复现 Android 的挂载链与 FUSE 权限语义。
 * 这份探针在真机上实测「共享存储根能否列举、能否写删、挂载链上有几个符号链接、
 * PathGuard 对样例 L1 文件 / 空目录 / 结构目录的判定」，把结论以 key=value 文本回传，
 * **不输出任何真实路径或文件名**，因此可以直接进已脱敏报告。
 *
 * 只做一次极小临时文件的创建与删除（立即清理），不读取任何用户文件内容。
 */
object StorageProbe {

    fun lines(context: Context): List<String> {
        val out = mutableListOf<String>()
        val root = try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (t: Throwable) {
            StorageRoots.DEFAULT_STORAGE_PATH
        }
        val rootFile = File(root)

        // 只输出类别，不输出真实路径（连系统挂载点也不出现在报告里）
        out += "storage_root_kind=" + when {
            root == "/sdcard" || root == "/mnt/sdcard" -> "sdcard-alias"
            root.startsWith("/storage/emulated/") -> "emulated"
            else -> "other"
        }
        out += "storage_root_exists=" + rootFile.exists()
        val children = try { rootFile.listFiles() } catch (t: Throwable) { null }
        out += "storage_root_listable=" + (children != null)
        out += "storage_root_child_count=" + (children?.size ?: -1)
        out += "storage_root_writable=" + try { rootFile.canWrite() } catch (t: Throwable) { false }
        out += "storage_root_structural=" + StorageRoots.isStructuralStorageDir(root)
        out += "mount_symlink_count=" + countMountSymlinks(rootFile)
        out += "write_delete_test=" + writeDeleteTest(rootFile)

        val guardContext = PathGuard.Context()
        val sampleFile = File(root, "Download/.junkmaster_probe.tmp").absolutePath
        val sampleEmptyDir = File(root, "Download/.junkmaster_probe_empty").absolutePath
        val structuralDir = File(root, "Android/data").absolutePath

        val gf = try { PathGuard.verify(sampleFile, guardContext) } catch (t: Throwable) { null }
        val gd = try { PathGuard.verify(sampleEmptyDir, guardContext, true) } catch (t: Throwable) { null }
        val gs = try { PathGuard.verify(structuralDir, guardContext, true) } catch (t: Throwable) { null }

        out += "pathguard_sample_file=" + verdict(gf)
        out += "pathguard_sample_empty_dir=" + verdict(gd)
        out += "pathguard_sample_structural_dir=" + verdict(gs)

        // App 私有外部目录（无需所有文件访问权）作为对照
        val privateDir = try { context.getExternalFilesDir(null) } catch (t: Throwable) { null }
        out += "private_external_dir_exists=" + (privateDir?.exists() == true)
        out += "private_external_write_delete=" +
                if (privateDir != null) writeDeleteTest(privateDir) else "n/a"

        return out.map { it.replace('\n', ' ').replace('\r', ' ') }
    }

    private fun verdict(result: PathGuard.Result?): String = when {
        result == null -> "error"
        result.allowed -> "allowed"
        else -> "denied:" + result.reason
    }

    /** 统计从共享存储根到 / 的父链上有多少个符号链接（只给数量，不给路径）。 */
    private fun countMountSymlinks(root: File): Int {
        var count = 0
        var current: File? = root
        val seen = mutableSetOf<String>()
        while (current != null && seen.add(current.absolutePath)) {
            try {
                if (Files.isSymbolicLink(current.toPath())) count++
            } catch (t: Throwable) {
                // ignore
            }
            current = current.parentFile
        }
        return count
    }

    /** 创建 1 字节临时文件再删除，验证 NIO 删除链路是否可用；无论如何都清理。 */
    private fun writeDeleteTest(dir: File): String {
        val probe = File(dir, ".junkmaster_probe_" + UUID.randomUUID().toString().take(8) + ".tmp")
        return try {
            if (!probe.createNewFile()) return "fail(createNewFile=false)"
            probe.writeText("x", Charsets.UTF_8)
            val deleted = probe.delete()
            if (deleted) "ok" else "fail(delete=false)"
        } catch (t: Throwable) {
            "fail(" + t.javaClass.simpleName + ")"
        } finally {
            try { if (probe.exists()) probe.delete() } catch (t: Throwable) { /* ignore */ }
        }
    }
}
