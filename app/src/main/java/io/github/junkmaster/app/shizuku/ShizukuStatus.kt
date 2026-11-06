package io.github.junkmaster.app.shizuku

/**
 * Shizuku 状态机枚举（纯 JVM）。
 * 正常链：NOT_INSTALLED → NOT_RUNNING → NEED_PERMISSION → READY
 * 异常：LOST（掉线）/ DENIED（拒权）/ PORTER（备选桥接）
 * operational=true 仅 READY，其余一律 fail-closed。
 */
enum class ShizukuStatus(val label: String, val operational: Boolean) {
    NOT_INSTALLED("未检测到 Shizuku 服务（未安装或未启动）", false),
    NOT_RUNNING("服务未运行", false),
    NEED_PERMISSION("需要授权", false),
    READY("已就绪", true),
    LOST("已掉线（fail-closed）", false),
    DENIED("权限被拒", false),
    PORTER("备选桥接", false)
}
