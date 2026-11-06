package io.github.junkmaster.app

import io.github.junkmaster.app.rule.BuiltInRules
import io.github.junkmaster.app.rule.ConsequenceCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：后果文案非空、recoverable 恒为 false、覆盖所有规则 */
class ConsequenceCatalogTest {

    @Test
    fun everyConsequenceHasNonBlankChineseText() {
        assertTrue(ConsequenceCatalog.all().isNotEmpty())
        ConsequenceCatalog.all().forEach { c ->
            assertTrue("${c.id} summary 为空", c.summary.isNotBlank())
            assertTrue("${c.id} detail 为空", c.detail.isNotBlank())
            assertTrue("${c.id} impact 为空", c.impact.isNotBlank())
            assertTrue("${c.id} id 为空", c.id.isNotBlank())
        }
    }

    @Test
    fun recoverableIsAlwaysFalse() {
        ConsequenceCatalog.all().forEach { c ->
            assertFalse("${c.id} 不得宣称可恢复", c.recoverable)
        }
        // 兜底文案也不可恢复
        assertFalse(ConsequenceCatalog.byId("does.not.exist").recoverable)
    }

    @Test
    fun catalogCoversEveryRule() {
        BuiltInRules.all.forEach { rule ->
            assertTrue(
                "ConsequenceCatalog 缺少 ${rule.consequenceId}（规则 ${rule.id}）",
                ConsequenceCatalog.has(rule.consequenceId)
            )
            assertEquals(rule.consequenceId, ConsequenceCatalog.byId(rule.consequenceId).id)
        }
    }

    @Test
    fun unknownIdFallsBackToSafeText() {
        val fallback = ConsequenceCatalog.byId("nope")
        assertEquals(ConsequenceCatalog.UNKNOWN, fallback.id)
        assertTrue(fallback.summary.isNotBlank())
    }
}
