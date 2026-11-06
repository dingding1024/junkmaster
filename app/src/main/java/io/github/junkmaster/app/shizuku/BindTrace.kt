package io.github.junkmaster.app.shizuku

/**
 * UserService 绑定过程记录（纯 JVM，无 Android 依赖，便于单测与诊断页共用）。
 *
 * 为什么需要它：真机上出现「Shizuku 已授权、binder 可 ping，但状态恒为服务未运行、
 * uid=-1、probe 为空」，说明 bindUserService 已发出但 onServiceConnected 从未回调。
 * 用户手上没有 adb、看不到 logcat，因此必须把这几步的结果留在 App 内，
 * 由「测试诊断」页随报告带出，才能判断卡在：
 *   1) 未 ping 到 Shizuku 服务；
 *   2) 未授权 / 授权被拒；
 *   3) bindUserService 抛异常（SystemService 拒绝、参数非法）；
 *   4) 已发起但 UserService 进程起不来（超时，无任何回调）；
 *   5) 回调已到但 probe 失败（binder 活着但事务失败）。
 *
 * 只保存步骤名、耗时、异常类名与简短消息，**绝不记录任何文件路径或文件名**。
 */
object BindTrace {

    /** 最多保留的条数（环形覆盖，避免长期运行涨内存）。 */
    const val MAX_ENTRIES: Int = 40

    data class Entry(
        val seq: Int,
        val ts: Long,
        val step: String,
        val ok: Boolean,
        val detail: String
    )

    private val entries: ArrayDeque<Entry> = ArrayDeque(MAX_ENTRIES)
    private var seq: Int = 0

    @Synchronized
    fun record(step: String, ok: Boolean, detail: String = "") {
        seq += 1
        if (entries.size >= MAX_ENTRIES) {
            entries.removeFirst()
        }
        entries.addLast(
            Entry(
                seq = seq,
                ts = System.currentTimeMillis(),
                step = step,
                ok = ok,
                detail = detail.replace('\n', ' ').take(240)
            )
        )
    }

    @Synchronized
    fun snapshot(): List<Entry> = entries.toList()

    @Synchronized
    fun reset() {
        entries.clear()
        seq = 0
    }

    /** 最近一条失败记录（诊断页 / 首页用来直接告诉用户「为什么没连上」）。 */
    @Synchronized
    fun lastFailure(): Entry? = entries.lastOrNull { !it.ok }

    /**
     * 渲染为纯文本（每行 `seq | 结果 | 步骤 | 耗时 | 详情`）。
     * 调用方负责再走一遍脱敏（诊断报告统一处理）。
     */
    fun render(limit: Int = MAX_ENTRIES): String {
        val list = snapshot().takeLast(limit.coerceAtLeast(1))
        if (list.isEmpty()) return "（无绑定记录）"
        val base = list.first().ts
        return list.joinToString("\n") { e ->
            val mark = if (e.ok) "OK  " else "FAIL"
            "  #" + e.seq + " +" + (e.ts - base) + "ms " + mark + " " +
                    e.step + (if (e.detail.isBlank()) "" else " :: " + e.detail)
        }
    }

    /** 一句话摘要，供 UI 直接展示。 */
    fun summary(): String {
        val fail = lastFailure() ?: return "绑定链路最近一次记录全部成功"
        return "最近一次失败：" + fail.step + "（" + (fail.detail.ifBlank { "无更多信息" }) + "）"
    }
}

/**
 * bind() 的结果：成功发起 / 未发起，并附带可直接展示给用户的原因。
 * 旧接口只返回 Boolean，UI 无法解释「为什么点了没反应」，这里补齐。
 */
data class BindResult(
    val started: Boolean,
    val reason: String
) {
    companion object {
        fun ok(reason: String = "已发起绑定"): BindResult = BindResult(true, reason)
        fun fail(reason: String): BindResult = BindResult(false, reason)
    }
}
