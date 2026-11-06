package io.github.junkmaster.app.clean

import java.io.File
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Paths

/**
 * 删除结果三级语义（2026-11-05 第四轮真机反馈修正）：
 *  - success=true：确实删除了目标（或目标已不存在），计入「已清理」。
 *  - safeSkip=true：**预期内的安全跳过**，例如「扫描时为空、删除前又写入了文件」「目录不可列举」
 *    「目标是符号链接」。这是防误删策略主动放弃，不是失败，绝不能计入失败数或触发
 *    连续失败 fail-closed —— 否则一批空目录里连续 5 个竞态就会把整个队列停掉。
 *  - 其余 false：真正的删除执行异常（AccessDeniedException / IO 异常），才计入失败数。
 */
data class DeleteOutcome(
    val success: Boolean,
    val message: String = "",
    val safeSkip: Boolean = false
)

/** 删除器抽象（便于纯 JVM 单测注入 fake）。 */
interface FileDeleter {
    fun delete(path: String, isDirectory: Boolean): DeleteOutcome
}

/**
 * 进程内 NIO 直删（不拼 shell 字符串）。纯 JVM。
 * App 侧基础 L1 与 UserService 侧深度删除都采用同样的直删思路。
 *
 * 2026-11-05 真机修正（第三轮反馈）：目录**不再用 walkFileTree 递归删除**。
 * L1 语义只允许删除「确实为空」的目录；扫描后若有新文件写入，递归删除会造成误删。
 * 现在删除目录前先要求「可列举 + 子项数为 0」，否则如实返回安全跳过。
 *
 * 2026-11-05 第四轮修正：上述「防误删跳过」改标 safeSkip=true，与硬失败区分。
 */
class NioFileDeleter : FileDeleter {

    override fun delete(path: String, isDirectory: Boolean): DeleteOutcome {
        return try {
            val target = Paths.get(path)
            if (Files.isSymbolicLink(target)) {
                return DeleteOutcome(false, "拒绝符号链接（目标本身）", safeSkip = true)
            }
            if (!Files.exists(target)) {
                return DeleteOutcome(true, "已不存在，视为成功")
            }
            if (isDirectory || Files.isDirectory(target)) {
                val children = try {
                    File(path).listFiles()
                } catch (t: Throwable) {
                    null
                }
                if (children == null) {
                    return DeleteOutcome(false, "目录不可列举（受保护或无权限），已跳过", safeSkip = true)
                }
                if (children.isNotEmpty()) {
                    return DeleteOutcome(false, "目录已非空，已跳过（防误删）", safeSkip = true)
                }
                // 确认真空目录：直接删除目录本身，不递归、不触碰任何子项。
                Files.deleteIfExists(target)
            } else {
                Files.deleteIfExists(target)
            }
            DeleteOutcome(true, "已删除")
        } catch (t: Throwable) {
            if (t is NoSuchFileException) {
                return DeleteOutcome(true, "已不存在，视为成功")
            }
            // 真正的 IO / 权限异常：硬失败，由连续失败策略判断是否需要 fail-closed
            DeleteOutcome(false, "删除失败：${t.javaClass.simpleName}")
        }
    }
}
