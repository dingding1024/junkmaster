package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * L2：卸载残留（只读 dry-run）。
 * 判定条件（计划第 3 节）：包未安装 + mtime 超过 30 天 + 已知残留名 + 删除前重查。
 * 「应用可能回来」：包仍安装时绝不判为残留（isCorpse 纯逻辑，可直接单测）。
 */
class CorpseFinder(
    private val provider: EntriesProvider,
    private val installedPackages: () -> Set<String>,
    private val evaluator: RuleEvaluator = RuleEvaluator(),
    private val nowMs: Long = System.currentTimeMillis()
) : ItemSource {

    override val name: String = "卸载残留（L2）"

    override fun collect(session: ScanSession, emit: (ScanItem) -> Unit) {
        val installed = installedPackages()
        provider().forEach { entry ->
            if (session.cancelled) return
            val owner = entry.ownerPackage ?: packageNameFromPath(entry.path) ?: return@forEach
            if (!isCorpse(owner, installed, entry.lastModifiedMs, nowMs)) return@forEach
            if (!KNOWN_RESIDUE_NAMES.any { entry.name.contains(it, ignoreCase = true) }) return@forEach
            emit(
                evaluator.toScanItem(
                    entry.path,
                    entry.sizeBytes,
                    entry.lastModifiedMs,
                    ScanCategory.CORPSE,
                    "包未安装 + 超过 30 天 + 已知残留名（删除前重查）"
                )
            )
        }
    }

    companion object {
        const val MIN_AGE_MS: Long = 30L * 24L * 60L * 60L * 1000L

        val KNOWN_RESIDUE_NAMES: Set<String> = setOf(
            "tencent", "micromsg", "weixin", "qq", "whatsapp", "telegram",
            "bluetooth", "backup", "cache", "data", "files"
        )

        fun packageNameFromPath(path: String): String? {
            val segments = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
            val idx = segments.indexOfFirst { it.equals("data", true) || it.equals("media", true) }
            return if (idx >= 0 && idx + 1 < segments.size) segments[idx + 1] else null
        }

        /** 纯逻辑：应用仍安装（可能回来）或不满 30 天 → 不是残留。 */
        fun isCorpse(
            ownerPackage: String?,
            installed: Set<String>,
            lastModifiedMs: Long,
            nowMs: Long,
            minAgeMs: Long = MIN_AGE_MS
        ): Boolean {
            if (ownerPackage == null) return false
            if (ownerPackage in installed) return false
            if (nowMs - lastModifiedMs < minAgeMs) return false
            return true
        }
    }
}
