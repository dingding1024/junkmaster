package io.github.junkmaster.app.shizuku

import io.github.junkmaster.app.clean.DeleteOutcome
import io.github.junkmaster.app.clean.FileDeleter

/**
 * 把 Shizuku UserService 的 NIO 直删包装成 CleanExecutor 可注入的 FileDeleter。
 * 这样深度清理同样享有 50 项一批 / 逐项复核 / 结构化日志 / fail-closed。
 *
 * 第四轮修正：UserService 主动放弃的项（复核拒绝 / 符号链接 / 非空目录）属于安全跳过，
 * 必须回传 safeSkip=true，否则深度清理也会被一批竞态目录拖垮。
 */
class BridgeFileDeleter(private val gateway: ShizukuGateway) : FileDeleter {

    override fun delete(path: String, isDirectory: Boolean): DeleteOutcome {
        val result = gateway.withBridge(
            ShellResult(false, "Shizuku 未就绪（fail-closed）")
        ) { bridge ->
            bridge.deletePaths(listOf(path))
        }
        if (result.ok) return DeleteOutcome(true, result.message)
        val m = result.message
        val safeSkip = m.contains("复核拒绝") ||
                m.contains("符号链接") ||
                m.contains("SKIP") ||
                m.contains("已跳过") ||
                m.contains("防误删")
        return DeleteOutcome(false, m, safeSkip = safeSkip)
    }
}
