package io.github.junkmaster.app.diag

/**
 * 诊断报告脱敏（纯 JVM，可直接单测）。
 *
 * 硬约束：默认且唯一外发版本就是脱敏版，屏显也用同一份，防止截图外泄。
 * 绝不输出：文件内容、聊天文字、登录态、账号、设备序列号、完整绝对路径、
 * 第三方应用包名、用户文件名、以及逐条清理日志。
 *
 * 2026-11-05 第四轮补充：清理日志里「安全跳过：…」是防误删策略主动放弃，
 * 必须与真实删除失败分开统计，否则诊断报告会把 8 次安全跳过显示成「失败 8 次」，
 * 与「已中止（系统性异常）」一起误导排障方向。
 */
object ReportSanitizer {

    /** 日志里安全跳过项的固定前缀（由 CleanExecutor 写入，代码生成、不含路径）。 */
    const val SAFE_SKIP_PREFIX: String = "安全跳过："

    /** 清理日志聚合结果：只含计数与代码生成的 reason，不含任何 path。 */
    data class JournalSummary(
        val entryCount: Int = 0,
        val successCount: Int = 0,
        val failedCount: Int = 0,
        val skippedCount: Int = 0,
        val ruleCounts: Map<String, Int> = emptyMap(),
        val latestTs: Long = 0L,
        val failedReasonCounts: Map<String, Int> = emptyMap(),
        val skippedReasonCounts: Map<String, Int> = emptyMap(),
        val lastReason: String = "",
        val lastRule: String = "",
        val lastOk: Boolean? = null
    )

    private val privateDataPrefix = Regex(
        "(?i)/(android/data|android/obb|data/data|data/user/\\d+|data/user_de/\\d+)/([A-Za-z0-9_.]+)"
    )
    private val storagePrefix = Regex("(?i)/(storage/emulated/\\d+|sdcard)")
    private val fileName = Regex("(?<=/)[^/]+$")
    private val controlChars = Regex("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]")

    private val ruleRe = Regex("\"rule\":\"([^\"]*)\"")
    private val okRe = Regex("\"ok\":(true|false)")
    private val tsRe = Regex("\"ts\":(\\d+)")
    private val reasonRe = Regex("\"reason\":\"([^\"]*)\"")

    /** 单行路径脱敏：包名 -> 占位符、共享存储前缀归一化、末段文件名隐去（保留扩展名）。 */
    fun sanitizePath(path: String): String = sanitizeLine(path)

    /**
     * 整段文本脱敏：逐行处理（避免跨行误吞）、去控制字符、按 limit 截断。
     * 用于 rawProbe / reason / 崩溃栈 / 用户附注。
     */
    fun sanitize(raw: String, limit: Int): String {
        val cleaned = controlChars.replace(raw, "")
        val sanitized = cleaned.lines().joinToString("\n") { sanitizeLine(it) }
        return if (sanitized.length > limit) {
            sanitized.take(limit) + "\n…(已截断)"
        } else {
            sanitized
        }
    }

    /** 只从 jsonl 行里抽取计数与原因；path 字段永远不会被读取或返回。 */
    fun aggregateJournal(lines: Sequence<String>): JournalSummary {
        var entries = 0
        var success = 0
        var failed = 0
        var skipped = 0
        var latest = 0L
        val rules = mutableMapOf<String, Int>()
        val failedReasons = mutableMapOf<String, Int>()
        val skippedReasons = mutableMapOf<String, Int>()
        var lastReason = ""
        var lastRule = ""
        var lastOk: Boolean? = null

        for (line in lines) {
            if (line.isBlank()) continue
            entries++
            val rule = ruleRe.find(line)?.groupValues?.get(1) ?: "unknown"
            rules[rule] = (rules[rule] ?: 0) + 1
            val ok = okRe.find(line)?.groupValues?.get(1)
            val reason = reasonRe.find(line)?.groupValues?.get(1) ?: ""
            val isSafeSkip = reason.startsWith(SAFE_SKIP_PREFIX)
            when (ok) {
                "true" -> success++
                "false" -> if (isSafeSkip) skipped++ else failed++
            }
            if (ok == "false" && reason.isNotBlank()) {
                val bucket = if (isSafeSkip) skippedReasons else failedReasons
                bucket[reason] = (bucket[reason] ?: 0) + 1
            }
            val ts = tsRe.find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            if (ts >= latest) {
                latest = ts
                lastReason = reason
                lastRule = rule
                lastOk = ok?.let { it == "true" }
            }
        }
        return JournalSummary(
            entryCount = entries,
            successCount = success,
            failedCount = failed,
            skippedCount = skipped,
            ruleCounts = rules,
            latestTs = latest,
            failedReasonCounts = failedReasons,
            skippedReasonCounts = skippedReasons,
            lastReason = lastReason,
            lastRule = lastRule,
            lastOk = lastOk
        )
    }

    private fun sanitizeLine(line: String): String {
        var s = privateDataPrefix.replace(line) { m ->
            "/" + m.groupValues[1] + "/<pkg>"
        }
        s = storagePrefix.replace(s, "/<storage>")
        s = fileName.replace(s) { m ->
            val name = m.value
            val dot = name.lastIndexOf('.')
            if (dot > 0 && dot < name.length - 1) "<file>" + name.substring(dot) else "<file>"
        }
        return s
    }
}
