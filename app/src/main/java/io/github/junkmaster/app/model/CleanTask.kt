package io.github.junkmaster.app.model

/**
 * 待删任务。batchIndex 为 50 项一批的批次号；guardVerified 标记删除前逐项 PathGuard 复核结果。
 */
data class CleanTask(
    val item: ScanItem,
    val batchIndex: Int,
    val guardVerified: Boolean = false
)
