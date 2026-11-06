package io.github.junkmaster.app

import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.rule.BuiltInRules
import io.github.junkmaster.app.rule.ConsequenceCatalog
import io.github.junkmaster.app.rule.RuleEvaluator
import io.github.junkmaster.app.rule.RuleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：规则命中 / 分级 / 默认勾选 / REPORT_ONLY / 白名单优先级 */
class RuleEvaluatorTest {

    private val evaluator = RuleEvaluator()

    @Test
    fun l1CacheItemIsSafeCheckedAndDirectDelete() {
        val item = evaluator.toScanItem("/sdcard/Android/data/com.example/cache/foo.tmp", 1024L)
        assertEquals(RiskLevel.L1_SAFE, item.riskLevel)
        assertEquals(DeleteMode.DIRECT_DELETE, item.deleteMode)
        assertTrue(item.isSafe)
        assertTrue(item.checked)
        assertEquals(ConsequenceCatalog.APP_CACHE, item.consequenceId)
        assertEquals(BuiltInRules.APP_CACHE.id, item.ruleId)
    }

    @Test
    fun downloadLargeIsReportOnlyAndNeverChecked() {
        val item = evaluator.toScanItem("/sdcard/Download/big_archive.zip", 999L)
        assertEquals(DeleteMode.REPORT_ONLY, item.deleteMode)
        assertFalse(item.checked)
        assertFalse(item.isSafe)
        assertTrue(item.reason.contains("仅报告"))
    }

    @Test
    fun oldApkIsReportOnly() {
        val item = evaluator.toScanItem("/sdcard/Download/old.apk", 999L)
        assertEquals(DeleteMode.REPORT_ONLY, item.deleteMode)
        assertFalse(item.checked)
    }

    @Test
    fun l3PathIsForbiddenRegardlessOfHint() {
        val item = evaluator.toScanItem(
            "/data/data/com.example/databases/msg.db",
            preferCategory = ScanCategory.APP_CACHE
        )
        assertEquals(RiskLevel.L3_FORBIDDEN, item.riskLevel)
        assertEquals(DeleteMode.FORBIDDEN, item.deleteMode)
        assertEquals(ScanCategory.L3_PROTECTED, item.category)
        assertFalse(item.checked)
        assertEquals(ConsequenceCatalog.L3_FORBIDDEN, item.consequenceId)
    }

    @Test
    fun preferCategoryBuildsCorpseWithL2Defaults() {
        val item = evaluator.toScanItem(
            "/sdcard/Android/data/com.removed/cache",
            preferCategory = ScanCategory.CORPSE
        )
        assertEquals(BuiltInRules.CORPSE.id, item.ruleId)
        assertEquals(RiskLevel.L2_CONFIRM, item.riskLevel)
        assertEquals(DeleteMode.CONFIRM_DELETE, item.deleteMode)
        assertFalse(item.checked) // L2 默认全不勾
        assertEquals(ConsequenceCatalog.CORPSE, item.consequenceId)
    }

    @Test
    fun whitelistDenyHasPriorityOverRules() {
        val repository = RuleRepository().withWhitelist(setOf("com.example/cache"))
        assertTrue(repository.isWhitelisted("/sdcard/Android/data/com.example/cache"))
        assertFalse(repository.isWhitelisted("/sdcard/Android/data/com.other/cache"))
        // 白名单命中即 deny，即便规则本身是 L1 可直清
        val rule = repository.forCategory(ScanCategory.APP_CACHE)
        assertEquals(BuiltInRules.APP_CACHE.id, rule?.id)
    }
}
