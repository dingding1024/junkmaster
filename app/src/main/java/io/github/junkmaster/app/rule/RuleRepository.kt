package io.github.junkmaster.app.rule

import io.github.junkmaster.app.model.ScanCategory

/**
 * 规则仓库：内置规则 + 用户白名单。白名单语义 deny > allow：
 * 即便规则命中，白名单路径也绝不进入删除队列（由 PathGuard 与 CleanExecutor 双重落实）。
 */
class RuleRepository(
    private val builtIn: List<CleanRule> = BuiltInRules.all,
    private val excludedSubstrings: Set<String> = emptySet()
) {
    fun rules(): List<CleanRule> = builtIn

    fun forCategory(category: ScanCategory): CleanRule? =
        builtIn.firstOrNull { it.category == category }

    fun forId(id: String): CleanRule? = builtIn.firstOrNull { it.id == id }

    fun isWhitelisted(path: String): Boolean {
        if (excludedSubstrings.isEmpty()) return false
        val n = RiskClassifier.normalize(path)
        return excludedSubstrings.any { n.contains(RiskClassifier.normalize(it)) }
    }

    fun whitelist(): Set<String> = excludedSubstrings

    fun withWhitelist(paths: Set<String>): RuleRepository = RuleRepository(builtIn, paths)
}
