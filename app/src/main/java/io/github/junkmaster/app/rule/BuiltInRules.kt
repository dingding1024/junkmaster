package io.github.junkmaster.app.rule

import io.github.junkmaster.app.model.ScanCategory

/**
 * 内置规则表（M2）。每条规则都有非空 consequenceId，且 ConsequenceCatalog 均有对应中文文案。
 *
 * 说明：部分规则（卸载残留、空目录、L3、未分类）由 Finder 按 preferCategory 直接构造，
 * 其 matcher 为 MATCH_NONE，不会在通用路径匹配中误命中；但仍保留完整规则 / 后果文案。
 */
object BuiltInRules {

    // ---- L1 安全 ----
    val APP_CACHE = CleanRule(
        id = "l1.app_cache",
        title = "应用外部 cache",
        category = ScanCategory.APP_CACHE,
        consequenceId = ConsequenceCatalog.APP_CACHE,
        matcher = PathMatcher(allSubstrings = setOf("/android/data/"), anySegments = setOf("cache")),
        description = "/sdcard/Android/data/<pkg>/cache 下的可再生成缓存"
    )

    val CODE_CACHE = CleanRule(
        id = "l1.code_cache",
        title = "code_cache",
        category = ScanCategory.CODE_CACHE,
        consequenceId = ConsequenceCatalog.CODE_CACHE,
        matcher = PathMatcher(allSubstrings = setOf("/android/data/"), anySegments = setOf("code_cache")),
        description = "/sdcard/Android/data/<pkg>/code_cache 运行时优化缓存"
    )

    val TEMP_FILE = CleanRule(
        id = "l1.temp_file",
        title = ".tmp 临时文件",
        category = ScanCategory.TEMP_FILE,
        consequenceId = ConsequenceCatalog.TEMP_FILE,
        matcher = PathMatcher(
            suffixes = setOf(
                ".tmp", ".temp", ".part", ".partial", ".crdownload", ".download",
                ".tmpl", ".dctmp", "~"
            )
        ),
        description = ".tmp / .temp / .part / .crdownload 临时文件与下载片段"
    )

    val THUMBNAIL = CleanRule(
        id = "l1.thumbnail",
        title = "缩略图",
        category = ScanCategory.THUMBNAIL,
        consequenceId = ConsequenceCatalog.THUMBNAIL,
        matcher = PathMatcher(anySegments = setOf(".thumbnails", "thumbnails")),
        description = ".thumbnails 缩略图缓存目录"
    )

    val EMPTY_DIR = CleanRule(
        id = "l1.empty_dir",
        title = "空目录",
        category = ScanCategory.EMPTY_DIR,
        consequenceId = ConsequenceCatalog.EMPTY_DIR,
        matcher = PathMatcher.MATCH_NONE,
        description = "经 EmptyDirFinder 判定为空的目录（删除前逐项复核仍为空）"
    )

    val CRASH_LOG = CleanRule(
        id = "l1.crash_log",
        title = "崩溃 / ANR 日志",
        category = ScanCategory.CRASH_LOG,
        consequenceId = ConsequenceCatalog.CRASH_LOG,
        matcher = PathMatcher(anySegments = setOf("anr", "tombstones", "traces")),
        description = "ANR traces / tombstones 崩溃诊断日志"
    )

    // ---- L2 需确认 ----
    val SYSTEM_LOG = CleanRule(
        id = "l2.system_log",
        title = "系统日志",
        category = ScanCategory.SYSTEM_LOG,
        consequenceId = ConsequenceCatalog.SYSTEM_LOG,
        matcher = PathMatcher(anySegments = setOf("log", "logs")),
        description = "系统 / 应用运行日志（逐项确认）"
    )

    val CORPSE = CleanRule(
        id = "l2.corpse",
        title = "卸载残留",
        category = ScanCategory.CORPSE,
        consequenceId = ConsequenceCatalog.CORPSE,
        matcher = PathMatcher.MATCH_NONE,
        description = "包未安装 + mtime > 30 天 + 已知残留名 + 删除前重查"
    )

    val CHAT_CACHE = CleanRule(
        id = "l2.chat_cache",
        title = "聊天附件缓存",
        category = ScanCategory.CHAT_CACHE,
        consequenceId = ConsequenceCatalog.CHAT_CACHE,
        matcher = PathMatcher(allSubstrings = setOf("/android/media/")),
        description = "聊天应用 /Android/media 下的图片 / 语音 / 附件缓存（逐项确认）"
    )

    // ---- 仅报告（恒不删） ----
    val DOWNLOAD_LARGE = CleanRule(
        id = "report.download_large",
        title = "Download 大文件",
        category = ScanCategory.DOWNLOAD_LARGE,
        consequenceId = ConsequenceCatalog.DOWNLOAD_LARGE,
        matcher = PathMatcher(anySegments = setOf("download", "downloads")),
        description = "Download 目录中的大文件，仅报告大小与路径"
    )

    val OLD_APK = CleanRule(
        id = "report.old_apk",
        title = "旧 APK",
        category = ScanCategory.OLD_APK,
        consequenceId = ConsequenceCatalog.OLD_APK,
        matcher = PathMatcher(suffixes = setOf(".apk", ".apks", ".xapk")),
        description = "旧安装包，仅报告不删"
    )

    /**
     * 第七轮反馈第 4 条：超过 1 个月未改动的大文件索引。
     * 判定不靠路径匹配（任何目录都可能有大文件），由 LargeFileIndex 按「体积 + 天数」决定，
     * 因此 matcher 为 MATCH_NONE，不会在通用路径匹配中被误命中。
     */
    val LARGE_OLD = CleanRule(
        id = "report.large_old",
        title = "超过 30 天的大文件",
        category = ScanCategory.LARGE_FILE_OLD,
        consequenceId = ConsequenceCatalog.LARGE_OLD,
        matcher = PathMatcher.MATCH_NONE,
        description = "体积 >= 50 MB 且最后修改超过 30 天的文件，仅索引不删"
    )

    // ---- L3 / 兜底 ----
    val L3 = CleanRule(
        id = "l3.protected",
        title = "L3 受保护路径",
        category = ScanCategory.L3_PROTECTED,
        consequenceId = ConsequenceCatalog.L3_FORBIDDEN,
        matcher = PathMatcher.MATCH_NONE,
        description = "硬编码禁止：/data/data、/system、DCIM/Pictures/Camera、Android/obb、databases、shared_prefs、凭据路径"
    )

    val UNKNOWN = CleanRule(
        id = "fallback.unknown",
        title = "未分类",
        category = ScanCategory.UNKNOWN,
        consequenceId = ConsequenceCatalog.UNKNOWN,
        matcher = PathMatcher.MATCH_NONE,
        description = "未命中任何已知规则，保守禁止"
    )

    val all: List<CleanRule> = listOf(
        APP_CACHE, CODE_CACHE, TEMP_FILE, THUMBNAIL, EMPTY_DIR, CRASH_LOG,
        SYSTEM_LOG, CORPSE, CHAT_CACHE,
        DOWNLOAD_LARGE, OLD_APK, LARGE_OLD,
        L3, UNKNOWN
    )

    val byId: Map<String, CleanRule> = all.associateBy { it.id }
}
