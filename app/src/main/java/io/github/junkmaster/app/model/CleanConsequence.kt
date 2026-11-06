package io.github.junkmaster.app.model

/**
 * 「删除后会有什么后果」中文说明。每条扫描结果在 UI 常显。
 *
 * 硬性约束：recoverable 恒为 false —— 用只读属性实现，构造参数无法覆盖，
 * 杜绝「可撤销 / 可回滚」这类不实宣称（Android 无系统回收站）。
 */
data class CleanConsequence(
    val id: String,
    /** 一句话后果（UI 后果卡主文案） */
    val summary: String,
    /** 详细说明 */
    val detail: String,
    /** 影响范围 */
    val impact: String,
    val riskLevel: RiskLevel
) {
    val recoverable: Boolean get() = false
}
