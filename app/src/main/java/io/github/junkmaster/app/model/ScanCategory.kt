package io.github.junkmaster.app.model

/**
 * 扫描分类 → 风险级别 / 删除模式的固定映射（三级模型单一事实来源）。
 * label 为中文显示名（纯 JVM 常量，供 UI 与单测使用）。
 */
enum class ScanCategory(
    val label: String,
    val riskLevel: RiskLevel,
    val deleteMode: DeleteMode
) {
    // ---- L1 安全：一键直清 ----
    APP_CACHE("应用外部 cache", RiskLevel.L1_SAFE, DeleteMode.DIRECT_DELETE),
    CODE_CACHE("code_cache", RiskLevel.L1_SAFE, DeleteMode.DIRECT_DELETE),
    TEMP_FILE(".tmp 临时文件", RiskLevel.L1_SAFE, DeleteMode.DIRECT_DELETE),
    THUMBNAIL("缩略图", RiskLevel.L1_SAFE, DeleteMode.DIRECT_DELETE),
    EMPTY_DIR("空目录", RiskLevel.L1_SAFE, DeleteMode.DIRECT_DELETE),
    CRASH_LOG("崩溃 / ANR 日志", RiskLevel.L1_SAFE, DeleteMode.DIRECT_DELETE),

    // ---- L2 需确认 ----
    SYSTEM_LOG("系统日志", RiskLevel.L2_CONFIRM, DeleteMode.CONFIRM_DELETE),
    CORPSE("卸载残留", RiskLevel.L2_CONFIRM, DeleteMode.CONFIRM_DELETE),
    CHAT_CACHE("聊天附件缓存", RiskLevel.L2_CONFIRM, DeleteMode.CONFIRM_DELETE),

    // ---- 仅报告（恒不删，UI 单独分区） ----
    DOWNLOAD_LARGE("Download 大文件", RiskLevel.L2_CONFIRM, DeleteMode.REPORT_ONLY),
    OLD_APK("旧 APK", RiskLevel.L2_CONFIRM, DeleteMode.REPORT_ONLY),

    /**
     * 第七轮反馈第 4 条：超过 1 个月未改动的大文件索引。
     * 只做「索引 + 路径」，绝不删除；受保护路径（相册 / 视频 / OBB 等）在索引里
     * 仍然按 L3 标记，只是为了让用户看到它们占了多大空间。
     */
    LARGE_FILE_OLD("超过 30 天的大文件", RiskLevel.L2_CONFIRM, DeleteMode.REPORT_ONLY),

    // ---- L3 禁止 ----
    L3_PROTECTED("L3 受保护路径", RiskLevel.L3_FORBIDDEN, DeleteMode.FORBIDDEN),
    UNKNOWN("未分类（保守禁止）", RiskLevel.L3_FORBIDDEN, DeleteMode.FORBIDDEN);

    /**
     * 索引 / 仅报告类：这类项的价值在「多大、多久没动过、在哪」，
     * UI 需要额外补一行最后修改时间，帮助用户判断能不能自己动手清理。
     */
    val isIndexLike: Boolean
        get() = this == DOWNLOAD_LARGE || this == OLD_APK || this == LARGE_FILE_OLD
}
