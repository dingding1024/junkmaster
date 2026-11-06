package io.github.junkmaster.app.rule

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanCategory

/**
 * 内置清理规则。每条规则都必须有非空 consequenceId，且能在 ConsequenceCatalog 找到文案。
 */
data class CleanRule(
    val id: String,
    val title: String,
    val category: ScanCategory,
    val consequenceId: String,
    val matcher: PathMatcher,
    val description: String
) {
    val riskLevel: RiskLevel get() = category.riskLevel
    val deleteMode: DeleteMode get() = category.deleteMode
}
