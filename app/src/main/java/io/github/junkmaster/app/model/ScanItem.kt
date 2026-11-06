package io.github.junkmaster.app.model

/**
 * 一条扫描结果（dry-run 只读产物）。UI 每行都据此显示安全徽标 + 后果卡 + 完整路径与大小。
 */
data class ScanItem(
    val id: String,
    /** 绝对路径 */
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val category: ScanCategory,
    val riskLevel: RiskLevel,
    val deleteMode: DeleteMode,
    /** 关联 ConsequenceCatalog 的文案 id，必须非空且有对应文案 */
    val consequenceId: String,
    val ruleId: String,
    val lastModifiedMs: Long = 0L,
    /** 「是否安全」徽标：仅 L1 为 true */
    val isSafe: Boolean = false,
    /** L3 拦截原因 / L2 残留说明 / REPORT_ONLY 说明 */
    val reason: String = "",
    /** UI 勾选态：L1 默认 true，L2/L3/REPORT_ONLY 默认 false */
    val checked: Boolean = false,
    /**
     * 是否需要 Shizuku（shell UID）权限才能真正删除。
     * 老板补充的产品主开关：**不开 Shizuku 时基础安全清扫照常可用**，
     * 只有 /sdcard/Android/data/<pkg>/cache 等「深度项」会被标记为此类，
     * 在扫描结果里单列，并且必须走 Shizuku UserService 删除。
     */
    val requiresShizuku: Boolean = false
)
