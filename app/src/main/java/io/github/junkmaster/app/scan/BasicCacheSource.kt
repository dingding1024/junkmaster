package io.github.junkmaster.app.scan

import io.github.junkmaster.app.model.ScanCategory
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.RuleEvaluator

/**
 * L1 基础安全项：共享存储里「明确缓存目录 + 明确可再生临时后缀」的文件（只读 dry-run）。
 *
 * 为什么需要它（2026-11-05 第四轮真机反馈）：
 *   vc4 的诊断报告显示 `rules=l1.empty_dir=76`，178 个待清理项全是空目录、释放 0 B。
 *   原来的 TempFileSource 只按扩展名扫，而共享存储里的临时文件绝大多数落在应用自建的
 *   cache / tmp 目录里；真正有体积的应用缓存又在 /sdcard/Android/data（需 Shizuku）。
 *   结果「不开 Shizuku 的基础清扫」除了删空目录没有任何实际产出，用户体感就是清不动。
 *
 * 安全边界（宁可漏扫，不可误删）：
 *  - 只处理**文件**，且 sizeBytes > 0（0 字节占位文件不作为清理成果）。
 *  - 路径必须同时满足「父目录段命中缓存目录白名单」+「文件名命中可再生后缀白名单」，
 *    单命中任一条件都不产出。用户自己放在 cache 目录里的 .jpg / .docx / 数据库不会被碰。
 *  - 排除 /android/data、/android/obb、/android/media：这些属于深度 / L2 范围，
 *    由 Shizuku UserService 或专门的 Finder 处理，绝不在这里默认勾选。
 *  - RuleEvaluator.evaluate 仍会先跑 RiskClassifier.isForbiddenPath，DCIM / Pictures /
 *    Camera / databases / shared_prefs 等 L3 路径一律归为「已禁止」。
 *  - 复用 ScanCategory.TEMP_FILE（L1 / 默认勾选 / DIRECT_DELETE），不新增风险规则。
 *
 * 纯 JVM，不依赖 android.*，可直接注入假 FileEntry 做单测。
 */
class BasicCacheSource(
    provider: EntriesProvider,
    evaluator: RuleEvaluator = RuleEvaluator()
) : FilteringItemSource("共享存储缓存文件（L1 基础）", provider, evaluator) {

    override fun map(entry: FileEntry): ScanItem? {
        if (entry.isDirectory) return null
        if (entry.sizeBytes <= 0L) return null

        val path = entry.path.replace('\\', '/').lowercase()
        if (EXCLUDED_SUBSTRINGS.any { path.contains(it) }) return null

        val segments = path.split('/').filter { it.isNotEmpty() }
        // dropLast(1) = 只看父目录链，文件名本身不算命中
        val inCacheDir = segments.dropLast(1).any { it in CACHE_DIR_SEGMENTS }
        if (!inCacheDir) return null

        val name = entry.name.lowercase()
        // 只认「明确可再生的扩展名」，不用文件名前缀猜 —— cache_xxx.bin 之类的
        // 可能是应用资源/数据库，宁可漏扫也绝不默认勾选删除（零误删红线）。
        val regenerable = TempFileFilter.isTemp(name) ||
                name.endsWith(".cache") ||
                name.endsWith(".thumb")
        if (!regenerable) return null

        return evaluator.toScanItem(
            entry.path,
            entry.sizeBytes,
            entry.lastModifiedMs,
            ScanCategory.TEMP_FILE
        )
    }

    companion object {
        /** 明确的缓存 / 临时目录名（小写），不含任何用户内容目录。 */
        val CACHE_DIR_SEGMENTS: Set<String> = setOf(
            "cache", "caches", ".cache", "code_cache", "tmp", "temp", ".temp",
            "temporary", "thumbnail_cache", "image_cache", ".thumbnails", "thumbnails"
        )

        /** 这些区域交给 Shizuku / L2 Finder，基础模式不越界。 */
        val EXCLUDED_SUBSTRINGS: List<String> = listOf(
            "/android/data/", "/android/obb/", "/android/media/"
        )
    }
}
