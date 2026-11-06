package io.github.junkmaster.app.shizuku

/**
 * Shizuku 状态机（纯 JVM，便于纯 JVM 单测覆盖 fail-closed）。
 *
 * 硬性约束：binder 掉线立即切到 LOST，canDeepClean() 随之变 false，
 * 所有深度操作（deletePaths / trimCaches / clearCacheViaBinder）必须经由此判定。
 *
 * 真机前静态复核修正：授权成功不再直接置 READY。bindUserService 是异步的，
 * 授权回调与 onServiceConnected 之间必须保持不可放行态，否则会出现
 * 「UI 显示已解锁、bridge 仍为空」的假 READY。READY 只由 ShizukuBindPolicy
 * 在 Shizuku 存活 + 已授权 + bridge 存活三者同时成立时给出。
 */
class ShizukuStateMachine {

    var status: ShizukuStatus = ShizukuStatus.NOT_INSTALLED
        private set

    fun transition(next: ShizukuStatus) {
        status = next
    }

    /** 授权成功只代表「可以绑定 UserService」，不代表深度能力已就绪。 */
    fun onPermissionGranted() {
        if (status != ShizukuStatus.READY) {
            status = ShizukuStatus.NOT_RUNNING
        }
    }

    fun onPermissionDenied() {
        status = ShizukuStatus.DENIED
    }

    /** 掉线：fail-closed，立即中止所有深度操作。 */
    fun onBinderDead() {
        status = ShizukuStatus.LOST
    }

    /** 深度操作唯一放行条件。 */
    fun canDeepClean(): Boolean = status == ShizukuStatus.READY

    fun reset() {
        status = ShizukuStatus.NOT_INSTALLED
    }
}

/** 状态刷新后应采取的动作。 */
enum class BindAction {
    NONE,
    AUTO_BIND,
    WAIT_SHIZUKU,
    WAIT_PERMISSION
}

/**
 * 绑定决策（纯 JVM，无 Android 依赖，便于单测覆盖真机前的状态分支）。
 *
 * 四个分支：
 *  - Shizuku 未运行 -> NOT_RUNNING / WAIT_SHIZUKU（基础模式不受影响）
 *  - 未授权 -> NEED_PERMISSION / WAIT_PERMISSION
 *  - 已授权但 bridge 未连接（冷启动、在 Shizuku Manager 里授权过、掉线恢复）
 *    -> NOT_RUNNING / AUTO_BIND，自动补绑定，无需用户手动点「绑定」
 *  - 三者齐备 -> READY / NONE
 *
 * READY 绝不由「授权成功」单独推出：bindUserService 是异步的。
 */
object ShizukuBindPolicy {

    data class Decision(val status: ShizukuStatus, val action: BindAction)

    fun decide(
        shizukuAlive: Boolean,
        permissionGranted: Boolean,
        bridgeAlive: Boolean
    ): Decision = when {
        !shizukuAlive -> Decision(ShizukuStatus.NOT_RUNNING, BindAction.WAIT_SHIZUKU)
        !permissionGranted -> Decision(ShizukuStatus.NEED_PERMISSION, BindAction.WAIT_PERMISSION)
        !bridgeAlive -> Decision(ShizukuStatus.NOT_RUNNING, BindAction.AUTO_BIND)
        else -> Decision(ShizukuStatus.READY, BindAction.NONE)
    }
}
