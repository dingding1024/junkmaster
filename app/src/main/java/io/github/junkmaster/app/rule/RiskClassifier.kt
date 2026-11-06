package io.github.junkmaster.app.rule

import io.github.junkmaster.app.model.RiskLevel

/**
 * 风险分级 + L3 硬编码禁止判定（计划第 3 节）。纯 JVM。
 *
 * 关键：App 侧（CleanExecutor）与 UserService 侧（CleanerUserService.deletePaths）
 * 都经由 PathGuard.verify → 本对象的 isForbiddenPath 做校验，实现「双重校验」。
 *
 * 注意：/sdcard/Android/data/<pkg>/cache 属于 L1 允许清理范围（Shizuku 引擎），
 * 因此不能简单地把 "data" 当作禁止段；这里用精确前缀 / 子串区分。
 */
object RiskClassifier {

    /** 归一化后以这些前缀开头 = 系统 / 私有数据分区，禁止 */
    val FORBIDDEN_PREFIXES: List<String> = listOf(
        "/data/data/",
        "/data/user/",
        "/data/user_de/",
        "/data/system/",
        "/system/",
        "/vendor/",
        "/product/",
        "/apex/",
        "/metadata/"
    )

    /** 归一化后路径任意位置含这些子串 = 禁止（媒体、OBB、数据库、凭据） */
    val FORBIDDEN_SUBSTRINGS: List<String> = listOf(
        "/android/obb/",
        "/dcim/",
        "/pictures/",
        "/camera/",
        "/movies/",
        "/music/",
        "/databases/",
        "/shared_prefs/",
        "/no_backup/",
        "/accounts/",
        "/keystore/",
        "/credential",
        "credentials",
        "/passwords/",
        "/tokens/",
        "keychain"
    )

    fun normalize(path: String): String {
        val n = path.replace('\\', '/').lowercase()
        return if (n.startsWith("/")) n else "/$n"
    }

    fun isForbiddenPath(path: String): Boolean {
        val n = normalize(path)
        return FORBIDDEN_PREFIXES.any { n.startsWith(it) } ||
                FORBIDDEN_SUBSTRINGS.any { n.contains(it) }
    }

    /** 未命中任何规则时按最保守策略归入 L3 禁止，避免误删。 */
    fun classify(path: String, matchedRule: CleanRule?): RiskLevel {
        if (isForbiddenPath(path)) return RiskLevel.L3_FORBIDDEN
        return matchedRule?.riskLevel ?: RiskLevel.L3_FORBIDDEN
    }
}
