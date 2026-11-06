package io.github.junkmaster.app.model

/**
 * 三级安全模型（计划第 3 节）。纯 JVM，可被 App 侧与 UserService 侧共用，也可被单测直接加载。
 */
enum class RiskLevel(
    val code: String,
    val label: String,
    /** 是否允许进入删除队列 */
    val isDeletable: Boolean,
    /** 扫描结果默认勾选态：L1 默认全选，L2/L3 默认全不勾 */
    val defaultChecked: Boolean,
    /** 「是否安全」徽标：仅 L1 为安全 */
    val safe: Boolean
) {
    L1_SAFE("L1", "L1 安全", isDeletable = true, defaultChecked = true, safe = true),
    L2_CONFIRM("L2", "L2 需确认", isDeletable = true, defaultChecked = false, safe = false),
    L3_FORBIDDEN("L3", "L3 禁止", isDeletable = false, defaultChecked = false, safe = false)
}
