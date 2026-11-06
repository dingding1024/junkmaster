package io.github.junkmaster.app.clean

import io.github.junkmaster.app.rule.RiskClassifier
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 符号链接探测抽象。
 *
 * 抽出这个接口是为了让纯 JVM 单测能模拟「设备上的 /sdcard 是符号链接」这一真实语义：
 * 桌面 JVM 里该路径不存在，`Files.isSymbolicLink` 恒为 false，早期用例全绿
 * 却漏掉了真机 A3 阻断 bug（见 StorageRoots 注释）。
 */
interface SymlinkProbe {
    fun isSymbolicLink(path: String): Boolean

    /** 完全解析符号链接后的真实路径；不存在 / 平台不支持时返回 null。 */
    fun realPath(path: String): String?
}

/** 设备 / 运行时默认实现：NIO 查询，不做任何缓存（删除前必须反映当前文件系统状态）。 */
object NioSymlinkProbe : SymlinkProbe {
    override fun isSymbolicLink(path: String): Boolean = try {
        Files.isSymbolicLink(Paths.get(path))
    } catch (t: Throwable) {
        false
    }

    override fun realPath(path: String): String? = try {
        Paths.get(path).toRealPath().toString().replace('\\', '/')
    } catch (t: Throwable) {
        null
    }
}

/**
 * 删除前的路径安全校验（M2 / M4 / M5）。纯 JVM，不依赖任何 android.* 类，
 * 因此 App 侧（CleanExecutor）、扫描列表层（CleanerUserService.listCandidates）与
 * UserService 侧（deletePaths）可以调用同一个实现，落实「双重校验」。
 *
 * 校验顺序：
 *  1. 空路径 / `..` 逃逸
 *  2. canonical 归一化
 *  3. 共享存储结构目录（仅当目标是目录时）
 *  4. 符号链接：
 *     - 目标本身是符号链接 → 拒绝
 *     - 解析后真实路径命中 L3 / 含 `..` → 拒绝（防逃逸）
 *     - 父链上的符号链接：系统挂载链放行；其他链接仅当解析目标仍在共享存储挂载点内才放行
 *  5. L3 硬编码禁止（RiskClassifier，OS 无关的字符串判定）
 *  6. 用户白名单（deny > allow）
 */
object PathGuard {

    data class Result(
        val allowed: Boolean,
        val canonicalPath: String,
        val reason: String,
        /** 是否因 L3 硬编码规则被拦截（便于单测与 UI 展示） */
        val blockedByL3: Boolean = false
    )

    data class Context(
        /** 用户白名单（deny 优先于 allow） */
        val deniedSubstrings: Set<String> = emptySet(),
        val rejectSymlinks: Boolean = true,
        val rejectL3: Boolean = true,
        /** 可注入的探测实现，便于纯 JVM 单测模拟 Android 挂载链 */
        val probe: SymlinkProbe = NioSymlinkProbe
    )

    /**
     * @param targetIsDirectory 待删除目标是否为目录。目录额外受「共享存储结构目录」硬拦截保护，
     *        防止把 /sdcard/Android/data 这类骨架目录当成 L1 空目录删掉。
     */
    fun verify(
        rawPath: String,
        context: Context = Context(),
        targetIsDirectory: Boolean = false
    ): Result {
        if (rawPath.isBlank()) {
            return deny(rawPath, "路径为空", false)
        }
        // 拒绝 `..` 逃逸（含编码变体由上层规范化，这里做最基础的硬拦截）
        if (rawPath.contains("..")) {
            return deny(RiskClassifier.normalize(rawPath), "路径包含 .. 逃逸", false)
        }

        // OS 无关的字符串归一化，用于 L3 / 白名单判定（不依赖 Windows 盘符）
        val normalized = RiskClassifier.normalize(rawPath)

        val canonical: String = try {
            File(rawPath).canonicalPath.replace('\\', '/')
        } catch (t: Throwable) {
            return deny(normalized, "无法归一化路径：${t.javaClass.simpleName}", false)
        }

        // 结构目录保护：原始 / canonical / 解析后三种形态任一命中都拒绝。
        if (targetIsDirectory && (
                    StorageRoots.isStructuralStorageDir(normalized) ||
                            StorageRoots.isStructuralStorageDir(canonical)
                    )
        ) {
            return deny(canonical, "共享存储结构目录禁止删除（防误删骨架目录）", true)
        }

        // 真实路径逃逸检查：无论是否开启 rejectSymlinks 都做，防止链接把目标指向私有数据分区。
        val real = context.probe.realPath(rawPath)
        if (real != null) {
            val realNormalized = RiskClassifier.normalize(real)
            if (realNormalized.contains("..")) {
                return deny(canonical, "路径解析后含 .. 逃逸", false)
            }
            if (context.rejectL3 && RiskClassifier.isForbiddenPath(realNormalized)) {
                return deny(canonical, "符号链接解析后指向 L3 禁止路径", true)
            }
            if (targetIsDirectory && StorageRoots.isStructuralStorageDir(realNormalized)) {
                return deny(canonical, "共享存储结构目录禁止删除（解析后）", true)
            }
        }

        if (context.rejectSymlinks) {
            // 目标本身是符号链接：一律拒绝（walkFileTree 不跟随链接，但顶层目标必须保守）
            if (context.probe.isSymbolicLink(rawPath)) {
                return deny(canonical, "拒绝符号链接（目标本身）", false)
            }

            var parent = try {
                Paths.get(rawPath).parent
            } catch (t: Throwable) {
                null
            }
            while (parent != null) {
                val dir = parent.toString().replace('\\', '/')
                if (context.probe.isSymbolicLink(dir)) {
                    if (!StorageRoots.isSystemMountChain(dir) && !isResolvedMountLink(dir, context)) {
                        return deny(canonical, "路径含非系统符号链接（防逃逸）", false)
                    }
                    // 系统挂载链也不能被拿来指向私有数据分区
                    val target = context.probe.realPath(dir)
                    if (target != null &&
                        RiskClassifier.isForbiddenPath(RiskClassifier.normalize(target))
                    ) {
                        return deny(canonical, "系统挂载链指向 L3 禁止路径", true)
                    }
                }
                parent = parent.parent
            }
        }

        if (context.rejectL3 && RiskClassifier.isForbiddenPath(normalized)) {
            return deny(canonical, "L3 硬编码禁止路径（App 与 UserService 双重校验）", true)
        }

        if (context.deniedSubstrings.any { normalized.contains(RiskClassifier.normalize(it)) }) {
            return deny(canonical, "命中用户白名单（deny 优先于 allow）", false)
        }

        return Result(
            allowed = true,
            canonicalPath = canonical,
            reason = "允许删除",
            blockedByL3 = false
        )
    }

    /**
     * 厂商 ROM 可能在 /storage、/mnt 下增加命名不同的挂载别名。
     * 只要链接解析后的目标仍落在系统挂载点（/storage、/mnt、/data/media）且不在 L3 内，
     * 就按「系统挂载链」放行；用户数据区内解析不到或指向别处的链接依旧拒绝。
     */
    private fun isResolvedMountLink(dir: String, context: Context): Boolean {
        val target = context.probe.realPath(dir) ?: return false
        val n = RiskClassifier.normalize(target)
        val isMountPoint = n.startsWith("/storage/") ||
                n.startsWith("/mnt/") ||
                Regex("^/data/media/\\d+(?:/|$)").containsMatchIn(n)
        return isMountPoint && !RiskClassifier.isForbiddenPath(n)
    }

    private fun deny(path: String, reason: String, blockedByL3: Boolean) =
        Result(false, path, reason, blockedByL3)
}
