package io.github.junkmaster.app.scan

import io.github.junkmaster.app.shizuku.BindResult
import io.github.junkmaster.app.shizuku.ShizukuStatus

/**
 * 「进入深度确认」按钮的放行判定（第六轮反馈第 5 条）。
 *
 * 原实现只返回 "ok" / "empty" / "need_shizuku" 三个字符串，
 * 非 ok 时 UI 什么都不做，用户点下去毫无反应也看不到原因。
 * 这里把「为什么不能进」拆成可枚举的原因，UI 据此弹对应文案的对话框，
 * 并可直接跳转到 Shizuku 设置页。
 *
 * 纯 JVM（只依赖枚举与 BindResult），便于单测覆盖所有分支。
 */
data class DeepGateResult(
    val ok: Boolean,
    val reason: DeepDenyReason,
    /** 可直接展示的补充说明（含最近一次绑定失败原因），可能为空。 */
    val detail: String
) {
    companion object {
        fun allow(): DeepGateResult = DeepGateResult(true, DeepDenyReason.OK, "")
        fun deny(reason: DeepDenyReason, detail: String = ""): DeepGateResult =
            DeepGateResult(false, reason, detail)
    }
}

enum class DeepDenyReason {
    OK,

    /** 没有勾选任何 L2 项。 */
    NO_SELECTION,

    /** Shizuku 只装未启动，或服务已停。 */
    SERVICE_NOT_RUNNING,

    /** 已连上 Shizuku 但本 App 尚未授权。 */
    NEED_PERMISSION,

    /** 用户拒绝过授权。 */
    DENIED,

    /** 深度服务曾就绪但已掉线（fail-closed）。 */
    LOST,

    /** 正在绑定 UserService，尚未回调。 */
    BINDING,

    /** 绑定请求已发出但失败 / 超时，附具体原因。 */
    BIND_FAILED
}

object DeepGate {

    /**
     * @param selectedCount 已勾选的 L2（深度）项数量
     * @param status 当前 Shizuku 状态
     * @param lastBind 最近一次 bind() 结果，用于给出「失败在哪一步」
     */
    fun decide(
        selectedCount: Int,
        status: ShizukuStatus,
        lastBind: BindResult? = null
    ): DeepGateResult {
        if (selectedCount <= 0) {
            return DeepGateResult.deny(DeepDenyReason.NO_SELECTION)
        }
        return when (status) {
            ShizukuStatus.READY -> DeepGateResult.allow()
            ShizukuStatus.NEED_PERMISSION -> DeepGateResult.deny(DeepDenyReason.NEED_PERMISSION)
            ShizukuStatus.DENIED -> DeepGateResult.deny(DeepDenyReason.DENIED)
            ShizukuStatus.LOST -> DeepGateResult.deny(DeepDenyReason.LOST)
            ShizukuStatus.NOT_INSTALLED -> DeepGateResult.deny(DeepDenyReason.SERVICE_NOT_RUNNING)
            ShizukuStatus.NOT_RUNNING -> {
                if (lastBind != null && !lastBind.started) {
                    DeepGateResult.deny(DeepDenyReason.BIND_FAILED, lastBind.reason)
                } else {
                    DeepGateResult.deny(DeepDenyReason.BINDING, lastBind?.reason ?: "")
                }
            }
            ShizukuStatus.PORTER -> DeepGateResult.deny(
                DeepDenyReason.BIND_FAILED,
                "当前使用备选桥接，深度清理不可用"
            )
        }
    }
}
