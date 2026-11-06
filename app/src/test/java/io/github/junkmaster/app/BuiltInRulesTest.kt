package io.github.junkmaster.app

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.rule.BuiltInRules
import io.github.junkmaster.app.rule.ConsequenceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：每条内置规则都有 consequenceId 且文案齐全；三级默认值正确 */
class BuiltInRulesTest {

    @Test
    fun allRulesHaveNonBlankConsequenceId() {
        assertTrue(BuiltInRules.all.isNotEmpty())
        BuiltInRules.all.forEach { rule ->
            assertTrue("规则 ${rule.id} 缺少 consequenceId", rule.consequenceId.isNotBlank())
        }
    }

    @Test
    fun allConsequenceIdsHaveCatalogText() {
        val missing = ConsequenceCatalog.missingFor(
            BuiltInRules.all.map { it.id to it.consequenceId }
        )
        assertTrue("以下规则缺少后果文案：$missing", missing.isEmpty())
    }

    @Test
    fun ruleIdsAreUnique() {
        assertEquals(BuiltInRules.all.size, BuiltInRules.all.map { it.id }.toSet().size)
    }

    @Test
    fun l1RulesAreDirectDeleteAndDefaultChecked() {
        BuiltInRules.all.filter { it.riskLevel == RiskLevel.L1_SAFE }.forEach {
            assertEquals(DeleteMode.DIRECT_DELETE, it.deleteMode)
            assertTrue(it.riskLevel.defaultChecked)
            assertTrue(it.riskLevel.safe)
        }
    }

    @Test
    fun l2RulesAreNotDefaultChecked() {
        BuiltInRules.all.filter { it.riskLevel == RiskLevel.L2_CONFIRM }.forEach {
            assertFalseDefaultChecked(it.riskLevel)
        }
    }

    @Test
    fun reportOnlyRulesNeverDelete() {
        BuiltInRules.all.filter { it.deleteMode == DeleteMode.REPORT_ONLY }.forEach {
            assertTrue("${it.id} 必须恒为 REPORT_ONLY", !it.deleteMode.canDelete)
        }
    }

    @Test
    fun l3IsHardForbidden() {
        assertEquals(RiskLevel.L3_FORBIDDEN, BuiltInRules.L3.riskLevel)
        assertTrue(!BuiltInRules.L3.riskLevel.isDeletable)
        assertEquals(DeleteMode.FORBIDDEN, BuiltInRules.L3.deleteMode)
    }

    private fun assertFalseDefaultChecked(level: RiskLevel) {
        assertTrue("$level 默认应全不勾", !level.defaultChecked)
    }
}
