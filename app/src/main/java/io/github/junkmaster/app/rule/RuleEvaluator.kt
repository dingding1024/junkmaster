package io.github.junkmaster.app.rule

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem

/**
 * 路径 → 规则 → ScanItem 的求值器。纯 JVM，供各 Finder、聚合器与单测共用。
 * L3 判定优先级最高（硬编码禁止），未命中任何规则保守归入 L3。
 *
 * 双模式（老板补充要求）：requiresShizuku=true 的项只有在 Shizuku READY 时才是可删的 L1；
 * 若扫描期间 Shizuku 掉线，防御式降级为 REPORT_ONLY，绝不进入删除队列。
 */
class RuleEvaluator(
    private val repository: RuleRepository = RuleRepository(),
    private val shizukuAvailable: () -> Boolean = { true }
) {
    fun evaluate(path: String, preferCategory: ScanCategory? = null): CleanRule {
        if (RiskClassifier.isForbiddenPath(path)) {
            return repository.forCategory(ScanCategory.L3_PROTECTED) ?: BuiltInRules.L3
        }
        if (preferCategory != null) {
            repository.forCategory(preferCategory)?.let { return it }
        }
        return repository.rules().firstOrNull { it.matcher.matches(path) }
            ?: BuiltInRules.UNKNOWN
    }

    fun toScanItem(
        path: String,
        sizeBytes: Long = 0L,
        lastModifiedMs: Long = 0L,
        preferCategory: ScanCategory? = null,
        reason: String = "",
        /** 仅 Shizuku 深度来源（/sdcard/Android/data 应用缓存）传 true */
        requiresShizuku: Boolean = false
    ): ScanItem {
        val privilegedUnavailable = requiresShizuku && !shizukuAvailable()
        val rule = evaluate(path, preferCategory)
        val risk = RiskClassifier.classify(path, rule)
        val forbidden = risk == RiskLevel.L3_FORBIDDEN
        val mode = if (forbidden) {
            DeleteMode.FORBIDDEN
        } else if (privilegedUnavailable) {
            DeleteMode.REPORT_ONLY
        } else {
            rule.deleteMode
        }
        val category = if (forbidden) ScanCategory.L3_PROTECTED else rule.category
        val resolvedReason = when {
            forbidden -> if (reason.isBlank()) "L3 硬编码禁止（App 与 UserService 双重校验）" else reason
            privilegedUnavailable -> "深度项需 Shizuku 授权，当前未就绪：仅报告不删除"
            mode == DeleteMode.REPORT_ONLY -> "仅报告，本 App 不自动删除"
            else -> reason
        }
        return ScanItem(
            id = path,
            path = path,
            displayName = path.substringAfterLast('/').ifBlank { path },
            sizeBytes = sizeBytes,
            category = category,
            riskLevel = risk,
            deleteMode = mode,
            consequenceId = rule.consequenceId,
            ruleId = rule.id,
            lastModifiedMs = lastModifiedMs,
            isSafe = risk.safe,
            reason = resolvedReason,
            // L1 默认勾选；L2 / L3 / REPORT_ONLY 默认全不勾
            checked = risk.defaultChecked && mode.canDelete && !privilegedUnavailable,
            requiresShizuku = requiresShizuku
        )
    }
}
