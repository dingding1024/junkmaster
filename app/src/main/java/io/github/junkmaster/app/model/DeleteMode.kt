package io.github.junkmaster.app.model

/**
 * 删除模式。REPORT_ONLY 恒不入删除队列（Download 大文件 / 旧 APK）。
 */
enum class DeleteMode(val label: String, val canDelete: Boolean) {
    /** L1：一键直清，无二次弹窗 */
    DIRECT_DELETE("一键直清", true),
    /** L2：逐项勾选、独立确认页 + 强制不可恢复确认 */
    CONFIRM_DELETE("需确认", true),
    /** Download 大文件 / 旧 APK：只报告不删 */
    REPORT_ONLY("仅报告", false),
    /** L3：硬编码禁止 */
    FORBIDDEN("已禁止", false)
}
