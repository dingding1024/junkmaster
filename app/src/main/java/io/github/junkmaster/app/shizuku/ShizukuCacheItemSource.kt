package io.github.junkmaster.app.shizuku

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator
import io.github.junkmaster.app.scan.ItemSource
import io.github.junkmaster.app.scan.ScanSession

/**
 * Shizuku 深度扫描源：应用外部 cache / code_cache（L1，可直接清）。
 *
 * 关键：Android 11+ 普通 App 进程**连列举都做不到** /sdcard/Android/data，
 * 所以必须由运行在 shell UID 的 UserService 在它自己的进程内遍历，再把路径回传。
 * 这也是「开 Shizuku 才解锁类似 SD Maid 深度清理」的实质实现。
 *
 * 只读 dry-run；删除由 CleanExecutor + BridgeFileDeleter 走 UserService 完成。
 */
class ShizukuCacheItemSource(
    private val gateway: ShizukuGateway,
    private val evaluator: RuleEvaluator = RuleEvaluator(
        shizukuAvailable = { gateway.currentStatus() == ShizukuStatus.READY }
    )
) : ItemSource {

    override val name: String = "Shizuku 深度 · 应用缓存（L1，可直接清）"

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        if (gateway.currentStatus() != ShizukuStatus.READY) return
        val paths = gateway.withBridge(emptyList()) { bridge -> bridge.listCandidates("cache") }
        for (raw in paths) {
            if (session.cancelled) return
            val normalized = raw.replace('\\', '/').lowercase()
            if (!normalized.contains("/android/data/")) continue
            val segments = normalized.split('/').filter { it.isNotEmpty() }
            val category = when {
                "code_cache" in segments -> ScanCategory.CODE_CACHE
                "cache" in segments -> ScanCategory.APP_CACHE
                else -> continue
            }
            emit(
                evaluator.toScanItem(
                    raw,
                    preferCategory = category,
                    requiresShizuku = true
                )
            )
        }
    }
}
