package io.github.junkmaster.app.shizuku

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import io.github.junkmaster.app.clean.PathGuard
import java.io.File
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Shizuku UserService（M5）：运行在 shell UID (2000) 进程。
 *
 * 硬性约束：
 *  - deletePaths 用 NIO 在进程内**直接删除**，不拼任何 shell 删除字符串。
 *  - 每一项删除前 UserService 侧再次 PathGuard.verify（与 App 侧 = 双重校验），
 *    L3 / 非系统符号链接 / `..` 逃逸一律拒绝；`/sdcard` 这类系统挂载链别名放行
 *    （否则所有 /sdcard 项都会被误判「父路径含符号链接」，详见 clean/StorageRoots.kt）。
 *  - trimCaches 只实测 `cmd package trim-caches` / `pm trim-caches` 并记录**原始输出**，
 *    被拒则回退到 NIO 直删（由 ShizukuGateway 编排），绝不谎报释放量。
 *  - 不调用任何会删除应用全部数据的命令。
 *  - proguard-rules.pro 必须 keep 本类与无参构造（Shizuku 反射实例化）。
 */
class CleanerUserService : CleanerServiceStub() {

    @Volatile
    private var destroyed = false

    override fun probe(): ShellResult {
        val raw = buildString {
            append("uid=").append(Process.myUid()).append('\n')
            append("sdk=").append(Build.VERSION.SDK_INT).append('\n')
            append("user_service=alive")
        }
        return ShellResult(
            ok = true,
            message = "UserService 已就绪（shell 进程）",
            rawOutput = raw
        )
    }

    /**
     * dry-run：只读列举候选，绝不删除。
     *
     * filterJson 含 "media" → 列 /sdcard/Android/media（聊天附件缓存，L2）；
     * 否则 → **只列 /sdcard/Android/data 下 cache / code_cache 目录内的文件**（深度 L1）。
     * 绝不把应用数据文件回传给 App 侧，避免被误标成默认勾选的 L1 一键清理项。
     */
    override fun listCandidates(filterJson: String): List<String> {
        if (destroyed) return emptyList()
        val wantMedia = filterJson.contains("media", ignoreCase = true)
        val roots = if (wantMedia) {
            listOf("/sdcard/Android/media")
        } else {
            listOf("/sdcard/Android/data")
        }
        val out = mutableListOf<String>()
        for (root in roots) {
            val start = Paths.get(root)
            if (!Files.exists(start)) continue
            try {
                Files.walkFileTree(start, object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE
                        return if (out.size >= MAX_CANDIDATES) FileVisitResult.TERMINATE
                        else FileVisitResult.CONTINUE
                    }

                    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                        if (Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE
                        if (out.size >= MAX_CANDIDATES) return FileVisitResult.TERMINATE
                        val p = file.toString().replace('\\', '/')
                        val segments = p.lowercase().split('/').filter { it.isNotEmpty() }
                        if (!wantMedia && "cache" !in segments && "code_cache" !in segments) {
                            return FileVisitResult.CONTINUE
                        }
                        // 列表层就过滤 L3 / 非法路径；deletePaths 会再校验一次（双重）
                        if (!PathGuard.verify(p).allowed) return FileVisitResult.CONTINUE
                        out += p
                        return FileVisitResult.CONTINUE
                    }
                })
            } catch (t: Throwable) {
                // 单个 root 不可读时跳过，只读扫描不抛到 App 侧
            }
        }
        return out
    }

    /** NIO 进程内直删；双重校验的 UserService 侧。失败即停并如实报告。 */
    override fun deletePaths(paths: List<String>): ShellResult {
        if (destroyed) {
            return ShellResult(false, "UserService 已 destroy，拒绝删除")
        }
        var deleted = 0
        var freed = 0L
        var skipped = 0
        val log = StringBuilder()

        for (raw in paths) {
            val target = Paths.get(raw)
            // 双重校验：UserService 侧再次校验（App 侧 CleanExecutor 已校验一次）
            // 第四轮修正：复核拒绝 / 符号链接属于安全跳过，改为 continue，
            // 不再「一项被拒就中止整批」，与 App 侧 CleanExecutor 语义一致。
            val guard = PathGuard.verify(
                raw,
                targetIsDirectory = try { Files.isDirectory(target) } catch (t: Throwable) { false }
            )
            if (!guard.allowed) {
                skipped++
                log.append("SKIP(guard) ").append(raw).append('\n')
                continue
            }

            if (Files.isSymbolicLink(target)) {
                skipped++
                log.append("SKIP(symlink) ").append(raw).append('\n')
                continue
            }
            if (!Files.exists(target)) {
                deleted++
                log.append("OK(not-exists) ").append(raw).append('\n')
                continue
            }

            val size = try {
                if (Files.isDirectory(target)) 0L else Files.size(target)
            } catch (t: Throwable) {
                0L
            }

            try {
                if (Files.isDirectory(target)) {
                    // 与 App 侧 NioFileDeleter 一致：只删「可列举且确实为空」的目录，
                    // 绝不递归删除（扫描后有新文件写入时递归删除会造成误删）。
                    val children = try { File(raw).listFiles() } catch (t: Throwable) { null }
                    when {
                        children == null -> {
                            skipped++
                            log.append("SKIP(unreadable-dir) ").append(raw).append('\n')
                            continue
                        }
                        children.isNotEmpty() -> {
                            skipped++
                            log.append("SKIP(non-empty-dir) ").append(raw).append('\n')
                            continue
                        }
                        else -> Files.deleteIfExists(target)
                    }
                } else {
                    Files.deleteIfExists(target)
                }
                deleted++
                freed += size
                log.append("OK ").append(raw).append('\n')
            } catch (t: Throwable) {
                // 真正的 IO / 权限异常：硬失败，立即中止并如实报告
                return ShellResult(
                    false,
                    "NIO 直删失败：${t.javaClass.simpleName}",
                    deleted,
                    freed,
                    log.toString()
                )
            }
        }
        // BridgeFileDeleter 每次只传 1 个路径：全部被安全跳过时返回 ok=false + 明确标记，
        // 由 App 侧转成 safeSkip=true，既不谎报删除成功，也不计入失败 / 触发 fail-closed。
        if (deleted == 0 && skipped > 0) {
            return ShellResult(
                false,
                "安全跳过：$skipped 项（防误删 / 受保护目录）",
                0,
                0L,
                log.toString()
            )
        }
        return ShellResult(true, "删除完成", deleted, freed, log.toString())
    }

    /**
     * 实测 trim-caches（不押宝）：记录命令与原始输出；成功按实际释放量记账（此处无法精确
     * 计量，freedBytes 置 0 由 App 侧容量对比得出）；被拒 / 失败如实标记，交给 App 侧回退。
     */
    override fun trimCaches(desiredBytes: Long): TrimResult {
        val raw = StringBuilder()
        val attempts = listOf(
            arrayOf("cmd", "package", "trim-caches", desiredBytes.toString()),
            arrayOf("pm", "trim-caches", desiredBytes.toString())
        )
        for (cmd in attempts) {
            val (exit, output) = runCommand(cmd)
            raw.append(cmd.joinToString(" "))
                .append(" -> exit=").append(exit).append('\n')
                .append(output).append('\n')
            val denied = output.lowercase().contains("denied") ||
                    output.lowercase().contains("permission") ||
                    output.lowercase().contains("security")
            if (exit == 0 && !denied) {
                return TrimResult(true, raw.toString(), 0L, "trim-caches")
            }
        }
        return TrimResult(false, raw.toString(), 0L, "refused-fallback-to-nio")
    }

    /** 隐藏 API deleteApplicationCacheFiles 尽力尝试；signature 级权限失败时如实报告、不谎报。 */
    override fun clearCacheViaBinder(packageName: String): ShellResult {
        val raw = StringBuilder()
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val getPackageManager = activityThread.getDeclaredMethod("getPackageManager")
                .apply { isAccessible = true }
            val ipm = getPackageManager.invoke(null)
            val observerClass = Class.forName("android.content.pm.IPackageDataObserver")
            val method = ipm.javaClass.getDeclaredMethod(
                "deleteApplicationCacheFiles",
                String::class.java,
                observerClass
            )
            method.isAccessible = true
            method.invoke(ipm, packageName, null)
            raw.append("deleteApplicationCacheFiles 已发出（结果需真机实测确认）")
            ShellResult(true, "已尝试 Binder 清缓存", rawOutput = raw.toString())
        } catch (t: Throwable) {
            raw.append("隐藏 API 不可用：").append(t.javaClass.simpleName)
            ShellResult(false, "隐藏 API 不可用，如实报告（回退 NIO）", rawOutput = raw.toString())
        }
    }

    /** 读取他应用缓存字节；UserService 无 Context，失败返回 0，由 App 侧 StorageStatsClient 兜底。 */
    override fun queryCacheBytes(packageName: String): Long {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val app = activityThread.getDeclaredMethod("currentApplication")
                .apply { isAccessible = true }
                .invoke(null) as? Context
            if (app == null) {
                0L
            } else {
                val manager = app.getSystemService(Context.STORAGE_STATS_SERVICE)
                        as? StorageStatsManager
                if (manager == null) {
                    0L
                } else {
                    val stats = manager.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        packageName,
                        Process.myUserHandle()
                    )
                    stats.cacheBytes
                }
            }
        } catch (t: Throwable) {
            0L
        }
    }

    /** unbindUserService 不杀进程，需显式 destroy() 释放。 */
    override fun destroy() {
        destroyed = true
    }

    private fun runCommand(cmd: Array<String>): Pair<Int, String> = try {
        val process = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exit = try {
            process.waitFor()
        } catch (t: Throwable) {
            -1
        }
        Pair(exit, output)
    } catch (t: Throwable) {
        Pair(-1, t.javaClass.simpleName)
    }

    companion object {
        const val MAX_CANDIDATES: Int = 2000
    }
}
