package io.github.junkmaster.app.clean

/**
 * 删除执行策略（计划第 3 节）：50 项一批、系统性异常时 fail-closed 中止。
 *
 * 2026-11-05 第三轮修正：不再「单项删除失败即中止整批」。
 * 2026-11-05 第四轮修正：**连续失败只对「硬失败」计数**。
 *   「目录已非空，已跳过（防误删）」「目录不可列举」「拒绝符号链接」属于 DeleteOutcome.safeSkip，
 *   是防误删策略主动放弃，绝不能计入失败或触发中止 —— 真机上一批空目录里连续出现
 *   5 个扫描/删除竞态，就会把 178 项的队列在第 75 项停掉，并误报「系统性异常」。
 *   只有 AccessDeniedException 等真实删除执行异常连续 MAX_CONSECUTIVE_FAILURES 次，
 *   才说明权限被撤销 / 存储不可用 / Shizuku 掉线，此时 fail-closed 中止。
 */
object FailurePolicy {
    const val BATCH_SIZE: Int = 50
    const val MAX_CONSECUTIVE_FAILURES: Int = 5
}
