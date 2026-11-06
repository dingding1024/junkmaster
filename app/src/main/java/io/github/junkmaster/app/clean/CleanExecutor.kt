package io.github.junkmaster.app.clean

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem

data class CleanProgress(
    val processed: Int,
    val total: Int,
    val batchIndex: Int,
    val stopped: Boolean
)

/**
 * 清理报告：同时给出「已清理项合计」与「可用空间变化」，并说明两者可能不等。
 * 纯 JVM。
 *
 * 四类结果互不混淆（2026-11-05 第四轮真机反馈修正）：
 *  - deletedCount：确实删除成功；freedBytes 只统计被删文件的字节数。
 *  - emptyDirDeletedCount：删除成功里属于「空目录」的数量（空目录不占存储空间，
 *    单独列出才能解释「已清理 67 项 / 释放 0 B」并不是失败）。
 *  - skippedCount：安全跳过（防误删 / 受保护目录 / 符号链接），**不是失败**，
 *    不计入 failedCount，也不参与连续失败中止判定。
 *  - failedCount：真正的删除执行异常（IO / 权限），只有连续多次才 fail-closed 中止。
 *  - guardRejectedCount：删除前 PathGuard 复核拒绝，同样是安全跳过。
 */
data class CleanReport(
    val requested: Int,
    val deletedCount: Int,
    val freedBytes: Long,
    val failedCount: Int,
    val batches: Int,
    val stoppedEarly: Boolean,
    val stopReason: String,
    val capacityBefore: Long?,
    val capacityAfter: Long?,
    val guardRejectedCount: Int = 0,
    val guardRejectReasons: List<String> = emptyList(),
    val failedReasons: List<String> = emptyList(),
    val skippedCount: Int = 0,
    val skippedReasons: List<String> = emptyList(),
    val emptyDirDeletedCount: Int = 0
) {
    val capacityDelta: Long?
        get() = if (capacityBefore != null && capacityAfter != null) {
            capacityAfter - capacityBefore
        } else {
            null
        }

    /** 释放空间为 0 时是否属于「只清了空目录」这种正常情况。 */
    val onlyEmptyDirs: Boolean
        get() = freedBytes == 0L && deletedCount > 0 && deletedCount == emptyDirDeletedCount
}

/**
 * 「一键清理」可能同时产生两份报告：基础项（App 进程 NIO）与 Shizuku 深度项（UserService）。
 * 合并后统一展示，已删合计、失败项、安全跳过与安全拦截数都不丢失。
 */
fun CleanReport.combine(other: CleanReport): CleanReport = CleanReport(
    requested = requested + other.requested,
    deletedCount = deletedCount + other.deletedCount,
    freedBytes = freedBytes + other.freedBytes,
    failedCount = failedCount + other.failedCount,
    batches = batches + other.batches,
    stoppedEarly = stoppedEarly || other.stoppedEarly,
    stopReason = listOf(stopReason, other.stopReason)
        .filter { it.isNotBlank() }
        .joinToString("；"),
    capacityBefore = capacityBefore ?: other.capacityBefore,
    capacityAfter = other.capacityAfter ?: capacityAfter,
    guardRejectedCount = guardRejectedCount + other.guardRejectedCount,
    guardRejectReasons = (guardRejectReasons + other.guardRejectReasons).distinct(),
    failedReasons = (failedReasons + other.failedReasons).distinct(),
    skippedCount = skippedCount + other.skippedCount,
    skippedReasons = (skippedReasons + other.skippedReasons).distinct(),
    emptyDirDeletedCount = emptyDirDeletedCount + other.emptyDirDeletedCount
)

/**
 * 删除执行器（M4）。纯 JVM，便于纯 JVM 单测覆盖分批 / 失败策略 / 日志 / 逐项复核。
 *
 * 落实硬性约束：
 *  - L1 一键直清：调用方把默认勾选的 L1 项直接传入，无需二次弹窗。
 *  - 删除前**逐项重新** PathGuard 校验（App 侧；UserService 侧再校验一次 = 双重）。
 *  - 复核拒绝 = 安全拦截，跳过该项并继续处理其余项，如实计数与报因；**不中止整批**。
 *  - 删除器返回 safeSkip 的项 = 防误删主动放弃，单列 skippedCount，**不累加失败计数**。
 *  - 只有真正的删除执行异常才计入 failedCount；连续 MAX_CONSECUTIVE_FAILURES 次
 *    硬失败才 fail-closed 中止（权限被撤销 / 存储不可用 / 掉线）。
 *  - REPORT_ONLY / L3 / 未勾选恒不入删除队列。
 *  - 50 项一批；isCancelled 用于 Shizuku 掉线 fail-closed 立即中止。
 *  - 结构化日志写 files/clean_logs 目录下的 jsonl 文件（不记录文件内容、不上传）。
 */
class CleanExecutor(
    private val deleter: FileDeleter,
    private val guardContext: PathGuard.Context = PathGuard.Context(),
    private val journal: DeleteJournal? = null,
    private val capacityProbe: CapacityProbe? = null,
    private val batchSize: Int = FailurePolicy.BATCH_SIZE,
    private val capacityRoot: String = StorageRoots.DEFAULT_STORAGE_PATH,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {

    fun execute(
        items: List<ScanItem>,
        isCancelled: () -> Boolean = { false },
        onProgress: (CleanProgress) -> Unit = {}
    ): CleanReport {
        // 只保留「已勾选 + 允许删除 + 风险可删」的项；REPORT_ONLY / L3 恒被排除
        val queue = items.filter {
            it.checked && it.deleteMode.canDelete && it.riskLevel.isDeletable
        }

        val capacityBefore = capacityProbe?.usableBytes(capacityRoot)
        var deletedCount = 0
        var emptyDirDeletedCount = 0
        var freedBytes = 0L
        var failedCount = 0
        var skippedCount = 0
        var guardRejectedCount = 0
        var consecutiveFailures = 0
        val guardRejectReasons = mutableListOf<String>()
        val failedReasons = mutableListOf<String>()
        val skippedReasons = mutableListOf<String>()
        var batches = 0
        var stopped = false
        var stopReason = ""

        fun processed() = deletedCount + failedCount + skippedCount + guardRejectedCount

        for ((batchIndex, batch) in queue.chunked(batchSize.coerceAtLeast(1)).withIndex()) {
            batches++
            for (item in batch) {
                if (isCancelled()) {
                    stopped = true
                    stopReason = "已取消 / Shizuku 掉线（fail-closed）"
                    break
                }

                // 删除前逐项重新校验（双重校验的 App 侧）；目录额外受结构目录硬拦截
                val isDir = item.category == ScanCategory.EMPTY_DIR
                val guard = PathGuard.verify(item.path, guardContext, isDir)
                if (!guard.allowed) {
                    guardRejectedCount++
                    if (guard.reason !in guardRejectReasons) guardRejectReasons += guard.reason
                    journal?.append(
                        JournalEntry(
                            timestampMs = nowMs(),
                            path = item.path,
                            sizeBytes = item.sizeBytes,
                            ruleId = item.ruleId,
                            batchIndex = batchIndex,
                            success = false,
                            reason = "安全跳过：复核拒绝：${guard.reason}"
                        )
                    )
                    onProgress(
                        CleanProgress(
                            processed = processed(),
                            total = queue.size,
                            batchIndex = batchIndex,
                            stopped = false
                        )
                    )
                    // 预期内的安全拦截：跳过本项，继续处理队列其余项
                    continue
                }

                val outcome = deleter.delete(item.path, isDir)
                val journalReason = when {
                    outcome.success -> outcome.message
                    outcome.safeSkip -> "安全跳过：${outcome.message}"
                    else -> outcome.message
                }
                journal?.append(
                    JournalEntry(
                        timestampMs = nowMs(),
                        path = item.path,
                        sizeBytes = item.sizeBytes,
                        ruleId = item.ruleId,
                        batchIndex = batchIndex,
                        success = outcome.success,
                        reason = journalReason
                    )
                )

                when {
                    outcome.success -> {
                        deletedCount++
                        if (item.category == ScanCategory.EMPTY_DIR) emptyDirDeletedCount++
                        freedBytes += item.sizeBytes
                        consecutiveFailures = 0
                    }
                    outcome.safeSkip -> {
                        // 防误删主动放弃：不是失败，不触发 fail-closed，也不打断连续失败计数
                        skippedCount++
                        if (outcome.message !in skippedReasons) skippedReasons += outcome.message
                    }
                    else -> {
                        failedCount++
                        if (outcome.message !in failedReasons) failedReasons += outcome.message
                        consecutiveFailures++
                        if (consecutiveFailures >= FailurePolicy.MAX_CONSECUTIVE_FAILURES) {
                            stopped = true
                            stopReason = "连续 $consecutiveFailures 项删除执行失败，疑似权限或存储异常，已中止（fail-closed）"
                        }
                    }
                }

                onProgress(
                    CleanProgress(
                        processed = processed(),
                        total = queue.size,
                        batchIndex = batchIndex,
                        stopped = stopped
                    )
                )
                if (stopped) break
            }
            if (stopped) break
        }

        val capacityAfter = capacityProbe?.usableBytes(capacityRoot)
        return CleanReport(
            requested = queue.size,
            deletedCount = deletedCount,
            freedBytes = freedBytes,
            failedCount = failedCount,
            batches = batches,
            stoppedEarly = stopped,
            stopReason = stopReason,
            capacityBefore = capacityBefore,
            capacityAfter = capacityAfter,
            guardRejectedCount = guardRejectedCount,
            guardRejectReasons = guardRejectReasons.toList(),
            failedReasons = failedReasons.toList(),
            skippedCount = skippedCount,
            skippedReasons = skippedReasons.toList(),
            emptyDirDeletedCount = emptyDirDeletedCount
        )
    }
}
