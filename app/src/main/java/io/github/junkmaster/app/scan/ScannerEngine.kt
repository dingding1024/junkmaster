package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanItem

/**
 * 扫描编排器（M3）。
 *
 * 硬约束：**扫描阶段纯 dry-run 只读，绝不删除**。ItemSource 只产出 ScanItem，
 * 引擎只收集与聚合。单个来源失败不阻断其它来源；支持进度回调与随时取消。
 * 纯 JVM（无 android.* 依赖），可被纯 JVM 单测直接加载。
 */
class ScannerEngine(
    private val sources: List<ItemSource>,
    private val aggregator: ScanResultAggregator = ScanResultAggregator()
) {
    fun scan(
        session: ScanSession = ScanSession(),
        onProgress: (ScanProgress) -> Unit = {}
    ): ScanResult {
        val collected = mutableListOf<ScanItem>()
        val total = sources.size.coerceAtLeast(1)

        val emit: (ScanItem) -> Unit = { item ->
            if (!session.cancelled) {
                collected += item
                session.foundCount++
            }
        }

        sources.forEachIndexed { index, source ->
            if (session.cancelled) return@forEachIndexed
            try {
                // 只读收集：绝不调用任何删除 API
                source.collect(session, emit)
            } catch (t: Throwable) {
                // 单个来源（如 MediaStore 查询）失败时跳过，不影响其余来源
            }
            session.scannedCount = index + 1
            onProgress(
                ScanProgress(
                    phase = source.name,
                    scanned = session.scannedCount,
                    found = session.foundCount,
                    percent = ((index + 1) * 100) / total,
                    cancelled = session.cancelled
                )
            )
        }

        return aggregator.aggregate(collected.toList()).copy(
            totalScanned = collected.size,
            cancelled = session.cancelled
        )
    }
}
