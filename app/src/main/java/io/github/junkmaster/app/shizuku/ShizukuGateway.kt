package io.github.junkmaster.app.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import rikka.shizuku.Shizuku

/**
 * Shizuku 网关（M5）：状态机 + bindUserService + binder 生命周期监听 + 掉线 fail-closed。
 *
 * 硬性约束：
 *  - 用 bindUserService（**禁用已弃用的 newProcess**）。
 *  - UserServiceArgs 显式 tag / processNameSuffix（R8 后类名不稳定，且 Shizuku 依赖这两者
 *    区分 UserService 实例与进程）。
 *  - unbind 前调用 destroy()（unbindUserService 不杀进程）。
 *  - binder 死亡立即切 LOST 并清空 bridge，所有深度操作经 withBridge / canDeepClean 判定。
 *
 * 第五轮真机反馈修正（「Shizuku 已授权成功，界面仍显示服务未运行，刷新无反应，
 * 点『绑定深度服务』『进入深度确认』都没有任何提示」）：
 *  1. **UserServiceArgs 补齐 Shizuku 官方要求的四个参数**
 *     （daemon / processNameSuffix / debuggable / version）。
 *     缺 processNameSuffix 时进程名与宿主 App 相同，Shizuku 侧启动 UserService 进程
 *     可能直接失败且不回调；缺 version 时升级 App 后 Shizuku 会复用旧版本 UserService
 *     进程（加载的是旧 APK 的 dex），表现为「明明装了新包、深度功能却永远连不上」。
 *  2. **bindInFlight 超时自愈**：原实现只在回调里复位标志，一旦 UserService 进程起不来
 *     （无 onServiceConnected、无 onServiceDisconnected、无异常），标志永久为 true，
 *     之后所有 bind()/刷新都静默短路 —— 这正是「刷新也没反应」的直接原因。
 *  3. **BindTrace 逐步留痕**：把 ping / 授权 / bindUserService 调用与异常 / 回调时机 /
 *     probe 结果记进环形缓冲，随「测试诊断」报告带出，无需 adb 即可定位卡点。
 *  4. **bind() 返回带原因的 BindResult**，UI 必须把原因显示出来，不再静默。
 */
class ShizukuGateway(
    private val appPackageName: String = "io.github.junkmaster.app",
    private val appVersionCode: Int = 0,
    private val debuggable: Boolean = false
) {
    companion object {
        const val TAG: String = "junkmaster-cleaner"

        /** 进程名后缀：与宿主 App 进程区分，Shizuku 靠它给 UserService 进程命名。 */
        const val PROCESS_NAME_SUFFIX: String = "cleaner"

        const val REQUEST_CODE: Int = 2026

        /**
         * 绑定超时：Shizuku fork UserService 进程通常 200~1500ms。
         * 超过这个时间没有任何回调，视为本次绑定失败并允许重试，
         * 避免 bindInFlight 永久卡死（真机上「刷新无反应」的根因）。
         */
        const val BIND_TIMEOUT_MS: Long = 8000L
    }

    private val machine = ShizukuStateMachine()
    private val _status = MutableStateFlow(machine.status)
    val status: StateFlow<ShizukuStatus> = _status

    private val _features = MutableStateFlow(ShizukuFeatures())
    val features: StateFlow<ShizukuFeatures> = _features

    /** 最近一次 bind() 的结果，供 Shizuku 设置页 / 首页直接展示原因。 */
    private val _lastBind = MutableStateFlow(BindResult(false, "尚未尝试绑定"))
    val lastBind: StateFlow<BindResult> = _lastBind

    @Volatile private var bridge: IPrivilegedBridge? = null
    @Volatile private var connection: ServiceConnection? = null
    @Volatile private var userServiceBinder: IBinder? = null
    @Volatile private var bindInFlight = false
    @Volatile private var bindStartedAt = 0L

    private val deathRecipient = IBinder.DeathRecipient { onLost() }

    private val userServiceArgs: Shizuku.UserServiceArgs by lazy {
        // 四个参数缺一不可（对齐 Shizuku 官方 sample）：
        //  daemon(false)            —— 不常驻，解绑即销毁，避免持权进程长期驻留
        //  processNameSuffix        —— UserService 进程独立命名，防止与宿主进程冲突导致启动失败
        //  debuggable                —— 必须与 APK 实际 debuggable 一致，否则 Shizuku 拒绝加载
        //  version(versionCode)     —— App 升级后强制重启 UserService，避免复用旧 dex 进程
        //  tag                      —— R8 混淆后类名不稳定，Shizuku 依赖 tag 定位实例
        Shizuku.UserServiceArgs(
            ComponentName(appPackageName, CleanerUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix(PROCESS_NAME_SUFFIX)
            .debuggable(debuggable)
            .version(appVersionCode)
            .tag(TAG)
    }

    init {
        try {
            Shizuku.addRequestPermissionResultListener(
                Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                    if (requestCode == REQUEST_CODE) {
                        onPermissionResult(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            )
        } catch (t: Throwable) {
            BindTrace.record("registerPermissionListener", false, t.describe())
        }
    }

    /**
     * 刷新状态：按 ShizukuBindPolicy 决策。READY 只在 bridge 存活时出现。
     * 检测到「Shizuku 已运行 + 已授权 + bridge 未连接」时自动补绑定，
     * 让冷启动也能直接进入深度模式；基础清扫全程不依赖这里的状态。
     */
    fun refreshStatus() {
        val decision = try {
            val alive = Shizuku.pingBinder()
            val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val bridgeAlive = bridge != null && isBridgeAlive()
            ShizukuBindPolicy.decide(alive, granted, bridgeAlive)
        } catch (t: Throwable) {
            BindTrace.record("refreshStatus", false, t.describe())
            ShizukuBindPolicy.Decision(ShizukuStatus.NOT_INSTALLED, BindAction.NONE)
        }
        if (decision.status != ShizukuStatus.READY) {
            dropBridge()
        }
        val changed = machine.status != decision.status
        machine.transition(decision.status)
        _status.value = decision.status
        if (changed) {
            BindTrace.record(
                "refreshStatus",
                decision.status != ShizukuStatus.LOST,
                "status=" + decision.status.name + " action=" + decision.action.name
            )
        }
        if (decision.action == BindAction.AUTO_BIND) {
            tryAutoBind()
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                // 已授权（常见于用户在 Shizuku Manager 里提前授权过）：refreshStatus 会自动补绑定
                BindTrace.record("requestPermission", true, "already granted，转为自动绑定")
                refreshStatus()
                return
            }
            BindTrace.record("requestPermission", true, "已发起授权请求")
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (t: Throwable) {
            BindTrace.record("requestPermission", false, t.describe())
            machine.transition(ShizukuStatus.DENIED)
            _status.value = ShizukuStatus.DENIED
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            BindTrace.record("onPermissionResult", false, "用户拒绝授权")
            machine.onPermissionDenied()
            _status.value = ShizukuStatus.DENIED
        } else {
            // 只到 NOT_RUNNING，READY 等 onServiceConnected + probe 成功后由 refreshStatus 给出
            BindTrace.record("onPermissionResult", true, "授权成功，开始绑定 UserService")
            machine.onPermissionGranted()
            _status.value = machine.status
            bind()
        }
    }

    /**
     * 幂等绑定：已有存活连接时直接返回；在途请求未超时则提示等待；超时则复位后重试。
     */
    fun bind(): BindResult {
        if (bridge != null && isBridgeAlive()) {
            refreshStatus()
            return publish(BindResult(true, "深度服务已连接"))
        }
        if (bindInFlight) {
            val waited = System.currentTimeMillis() - bindStartedAt
            if (waited < BIND_TIMEOUT_MS) {
                BindTrace.record("bind.skip", false, "绑定进行中，已等待 ${waited}ms")
                return publish(
                    BindResult(false, "绑定请求进行中（已等待 " + (waited / 1000) + " 秒），请稍候")
                )
            }
            // 超时自愈：清掉卡死的在途标志，继续走下面的重新绑定
            BindTrace.record(
                "bind.timeout",
                false,
                "超过 " + BIND_TIMEOUT_MS + "ms 未收到 onServiceConnected，复位后重试"
            )
            bindInFlight = false
            bindStartedAt = 0L
        }
        return try {
            if (!Shizuku.pingBinder()) {
                BindTrace.record("pingBinder", false, "Shizuku 服务未运行")
                bindInFlight = false
                bindStartedAt = 0L
                machine.transition(ShizukuStatus.NOT_RUNNING)
                _status.value = ShizukuStatus.NOT_RUNNING
                return publish(BindResult.fail("Shizuku 服务未运行：请先在 Shizuku 里启动服务"))
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                BindTrace.record("checkSelfPermission", false, "尚未授权")
                bindInFlight = false
                bindStartedAt = 0L
                refreshStatus()
                return publish(BindResult.fail("尚未授权 Shizuku 权限：请先点「请求 Shizuku 权限」"))
            }
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val cost = if (bindStartedAt > 0) System.currentTimeMillis() - bindStartedAt else -1L
                    bindInFlight = false
                    bindStartedAt = 0L
                    if (service == null) {
                        BindTrace.record("onServiceConnected", false, "service=null (${cost}ms)")
                        onLost()
                        return
                    }
                    BindTrace.record(
                        "onServiceConnected",
                        true,
                        "binder=" + runCatching { service.interfaceDescriptor }.getOrNull() +
                                " (" + cost + "ms)"
                    )
                    userServiceBinder = service
                    try {
                        service.linkToDeath(deathRecipient, 0)
                    } catch (t: Throwable) {
                        BindTrace.record("linkToDeath", false, t.describe())
                        onLost()
                        return
                    }
                    val svc = CleanerServiceStub.asInterface(service)
                    if (svc == null) {
                        BindTrace.record("asInterface", false, "接口转换失败")
                        onLost()
                        return
                    }
                    val b = ShizukuBridge(svc)
                    bridge = b
                    val probe = b.probe()
                    BindTrace.record(
                        "probe",
                        probe.ok,
                        "uid=" + parseUid(probe.rawOutput) + " sdk=" + parseSdk(probe.rawOutput) +
                                " msg=" + probe.message
                    )
                    _features.value = ShizukuFeatures(
                        sdkInt = parseSdk(probe.rawOutput),
                        uid = parseUid(probe.rawOutput),
                        rawProbe = probe.rawOutput,
                        shellCanDeleteAppDataCache = probe.ok
                    )
                    refreshStatus()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    BindTrace.record("onServiceDisconnected", false, "UserService 断开")
                    bindInFlight = false
                    bindStartedAt = 0L
                    onLost()
                }
            }
            connection = conn
            bindInFlight = true
            bindStartedAt = System.currentTimeMillis()
            BindTrace.record("bindUserService", true, argsSummary())
            Shizuku.bindUserService(userServiceArgs, conn)
            publish(BindResult(true, "已发起绑定，等待 UserService 连接"))
        } catch (t: Throwable) {
            bindInFlight = false
            bindStartedAt = 0L
            BindTrace.record("bindUserService", false, t.describe())
            // 绑定失败保持不可放行态；不置 LOST，避免自动重连被永久锁死
            machine.transition(ShizukuStatus.NOT_RUNNING)
            _status.value = ShizukuStatus.NOT_RUNNING
            publish(
                BindResult.fail("绑定请求被拒绝：" + t.javaClass.simpleName +
                        (t.message?.take(80)?.let { "（$it）" } ?: ""))
            )
        }
    }

    /**
     * 强制重绑：先真正解绑旧连接再重新发起。
     * 真机上 UserService 进程可能已崩溃但本地仍留有陈旧引用，
     * 只调 bind() 会被「已连接」或「进行中」短路，因此需要一个显式复位入口。
     */
    fun forceRebind(): BindResult {
        val conn = connection
        if (conn != null) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, conn, true) }
        }
        runCatching { userServiceBinder?.unlinkToDeath(deathRecipient, 0) }
        userServiceBinder = null
        bridge = null
        connection = null
        bindInFlight = false
        bindStartedAt = 0L
        machine.reset()
        _status.value = ShizukuStatus.NOT_INSTALLED
        BindTrace.record("forceRebind", true, "已解绑并清空本地引用，准备重新绑定")
        return bind()
    }

    /** unbindUserService 不杀进程，必须先 destroy()。 */
    fun unbind() {
        try {
            bridge?.destroy()
        } catch (t: Throwable) {
            // ignore
        }
        val conn = connection
        if (conn != null) {
            try {
                Shizuku.unbindUserService(userServiceArgs, conn, true)
            } catch (t: Throwable) {
                // ignore
            }
        }
        try {
            userServiceBinder?.unlinkToDeath(deathRecipient, 0)
        } catch (t: Throwable) {
            // ignore
        }
        userServiceBinder = null
        bridge = null
        connection = null
        bindInFlight = false
        bindStartedAt = 0L
        machine.reset()
        _status.value = ShizukuStatus.NOT_RUNNING
        BindTrace.record("unbind", true, "已手动解绑并销毁 UserService")
    }

    /** 深度操作唯一入口：非 READY 或 binder 不存活 → 立即拒绝（fail-closed）。 */
    fun <T> withBridge(fallback: T, block: (IPrivilegedBridge) -> T): T {
        if (!machine.canDeepClean()) return fallback
        val b = bridge ?: return fallback
        if (!b.isAlive) {
            onLost()
            return fallback
        }
        return try {
            block(b)
        } catch (t: Throwable) {
            onLost()
            fallback
        }
    }

    /**
     * trim-caches 实测 + 回退链：
     *  成功 → 用（按实际释放量，绝不谎报）；被拒 → NIO 直删 cache/code_cache；再失败仅报告原因。
     */
    fun trimCachesWithFallback(desiredBytes: Long): TrimResult =
        withBridge(TrimResult(false, "Shizuku 未就绪（fail-closed）", 0L, "unavailable")) { b ->
            val primary = b.trimCaches(desiredBytes)
            if (primary.ok) {
                primary
            } else {
                val candidates = b.listCandidates("cache")
                val deleted = b.deletePaths(candidates)
                TrimResult(
                    deleted.ok,
                    primary.rawOutput + "\n" + deleted.rawOutput,
                    deleted.freedBytes,
                    "nio-fallback"
                )
            }
        }

    fun currentStatus(): ShizukuStatus = machine.status

    /** 诊断页 / 设置页展示用：绑定链路最近记录。 */
    fun bindTraceText(): String = BindTrace.render()

    /** 诊断报告用：UserServiceArgs 的四个关键参数（不含路径，可直接外发）。 */
    fun userServiceArgsSummary(): String = argsSummary()

    /**
     * 一键诊断绑定链路（设置页按钮，结果可直接复制给开发者）。
     *
     * 只输出布尔值 / 整数 / 类名 / Shizuku 返回值与 BindTrace，
     * **不含任何文件路径、包名清单或用户数据**。
     */
    fun diagnose(): String {
        val sb = StringBuilder()
        fun line(key: String, value: Any?) {
            sb.append(key).append('=').append(value ?: "null").append('\n')
        }
        line("ping_binder", runCatching { Shizuku.pingBinder() }.getOrElse { false })
        line(
            "permission",
            runCatching {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    "granted"
                } else {
                    "not_granted"
                }
            }.getOrElse { "error:" + it.javaClass.simpleName }
        )
        line("shizuku_version", runCatching { Shizuku.getVersion() }.getOrElse { -1 })
        line("shizuku_uid", runCatching { Shizuku.getUid() }.getOrElse { -1 })
        line("current_status", machine.status.name)
        line("bridge_alive", bridge != null && isBridgeAlive())
        line("bind_in_flight", bindInFlight)
        if (bindInFlight && bindStartedAt > 0) {
            line("bind_wait_ms", System.currentTimeMillis() - bindStartedAt)
        }
        line("user_service_args", argsSummary())
        line("last_bind_started", _lastBind.value.started)
        line("last_bind_reason", _lastBind.value.reason)
        val f = _features.value
        line("user_service_uid", f.uid)
        line("user_service_sdk", f.sdkInt)
        line("can_delete_app_data_cache", f.shellCanDeleteAppDataCache)

        sb.append("bind_trace:\n").append(BindTrace.render()).append('\n')

        // 诊断动作本身也要留下痕迹，方便对比「诊断时」与「用户点击时」的差异
        BindTrace.record(
            "diagnose",
            true,
            "status=" + machine.status.name + " bridge=" + (bridge != null)
        )
        return sb.toString()
    }

    private fun isBridgeAlive(): Boolean = bridge?.isAlive == true

    private fun bindTimedOut(): Boolean =
        bindInFlight && bindStartedAt > 0 && System.currentTimeMillis() - bindStartedAt > BIND_TIMEOUT_MS

    /** 自动补绑定：仅在 Shizuku 存活且已授权时尝试，失败保持 NOT_RUNNING。 */
    private fun tryAutoBind() {
        if (bridge != null) return
        // 在途且未超时 → 等回调；超时 → 交给 bind() 复位后重试
        if (bindInFlight && !bindTimedOut()) return
        try {
            if (!Shizuku.pingBinder()) return
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return
            bind()
        } catch (t: Throwable) {
            bindInFlight = false
            bindStartedAt = 0L
            BindTrace.record("tryAutoBind", false, t.describe())
        }
    }

    /**
     * 清理本地失效引用（Shizuku 未运行 / 未授权 / bridge 已死），保持 fail-closed。
     *
     * 这里**不调用 destroy()**：Shizuku 的 UserService 进程可能被后续 bindUserService 复用，
     * 而 destroy() 会把实例永久标记为已销毁（listCandidates 返回空、deletePaths 拒绝删除），
     * 导致「撤销授权后重新授权」深度清理静默失效。显式解绑仍由 unbind() 负责。
     *
     * 也**不清 bindInFlight**：绑定在途时用户每次点刷新都会走到这里，
     * 若在此复位标志，会重复 bindUserService 并在 Shizuku 侧堆积多个 connection；
     * 在途请求的复位统一交给 bind() 的超时分支。
     */
    private fun dropBridge() {
        try {
            userServiceBinder?.unlinkToDeath(deathRecipient, 0)
        } catch (t: Throwable) {
            // ignore：binder 可能已随 Shizuku 一起死亡
        }
        userServiceBinder = null
        bridge = null
    }

    /** 掉线：立即中止所有深度操作（fail-closed）。刷新后会尝试自动重连。 */
    private fun onLost() {
        bindInFlight = false
        bindStartedAt = 0L
        bridge = null
        machine.onBinderDead()
        _status.value = ShizukuStatus.LOST
    }

    private fun publish(result: BindResult): BindResult {
        _lastBind.value = result
        return result
    }

    private fun argsSummary(): String =
        "tag=" + TAG + " suffix=" + PROCESS_NAME_SUFFIX + " daemon=false debug=" + debuggable +
                " version=" + appVersionCode + " class=" + CleanerUserService::class.java.simpleName

    private fun Throwable.describe(): String =
        javaClass.simpleName + (message?.take(120)?.let { ": $it" } ?: "")

    private fun parseSdk(raw: String): Int {
        val line = raw.lines().firstOrNull { it.startsWith("sdk=") } ?: return 0
        return line.removePrefix("sdk=").trim().toIntOrNull() ?: 0
    }

    private fun parseUid(raw: String): Int {
        val line = raw.lines().firstOrNull { it.startsWith("uid=") } ?: return -1
        return line.removePrefix("uid=").trim().toIntOrNull() ?: -1
    }
}
