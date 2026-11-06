package io.github.junkmaster.app

import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.rule.BuiltInRules
import io.github.junkmaster.app.rule.RiskClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 纯 JVM 单测：风险分级与 L3 硬编码禁止（含不误伤 Android/data/cache） */
class RiskClassifierTest {

    @Test
    fun androidDataCacheIsNotForbidden() {
        assertFalse(RiskClassifier.isForbiddenPath("/sdcard/Android/data/com.example/cache"))
    }

    @Test
    fun privateDataIsForbidden() {
        assertTrue(RiskClassifier.isForbiddenPath("/data/data/com.example/databases/x.db"))
        assertTrue(RiskClassifier.isForbiddenPath("/data/user/0/com.example/shared_prefs/x.xml"))
    }

    @Test
    fun obbAndMediaAreForbidden() {
        assertTrue(RiskClassifier.isForbiddenPath("/sdcard/Android/obb/com.example/main.obb"))
        assertTrue(RiskClassifier.isForbiddenPath("/sdcard/DCIM/Camera/a.jpg"))
        assertTrue(RiskClassifier.isForbiddenPath("/sdcard/Pictures/Screenshots/a.png"))
    }

    @Test
    fun credentialPathsAreForbidden() {
        assertTrue(RiskClassifier.isForbiddenPath("/sdcard/Android/data/com.example/credentials/token"))
        assertTrue(RiskClassifier.isForbiddenPath("/sdcard/keystore/seed"))
    }

    @Test
    fun classifyL1Rule() {
        assertEquals(
            RiskLevel.L1_SAFE,
            RiskClassifier.classify("/sdcard/Android/data/com.example/cache", BuiltInRules.APP_CACHE)
        )
    }

    @Test
    fun classifyL3OverridesAnyRule() {
        assertEquals(
            RiskLevel.L3_FORBIDDEN,
            RiskClassifier.classify("/data/data/com.example/cache", BuiltInRules.APP_CACHE)
        )
    }

    @Test
    fun unknownRuleIsConservativelyForbidden() {
        assertEquals(
            RiskLevel.L3_FORBIDDEN,
            RiskClassifier.classify("/some/random/path/file.bin", null)
        )
    }

    @Test
    fun normalizeIsOsIndependent() {
        assertEquals("/data/data/com.example", RiskClassifier.normalize("/data/data/com.example"))
        assertEquals("/sdcard/dcim/a.jpg", RiskClassifier.normalize("sdcard\\DCIM\\a.jpg"))
    }
}
