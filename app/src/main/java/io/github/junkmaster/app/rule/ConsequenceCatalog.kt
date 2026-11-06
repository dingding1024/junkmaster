package io.github.junkmaster.app.rule

import io.github.junkmaster.app.model.CleanConsequence
import io.github.junkmaster.app.model.RiskLevel

/**
 * 中文后果文案目录（计划第 3 节示例）。纯 JVM，供 UI 与纯 JVM 单测共用。
 * 每条 BuiltInRules 规则的 consequenceId 都在此有非空文案。
 */
object ConsequenceCatalog {
    // ---- L1 ----
    const val APP_CACHE = "cons.app_cache"
    const val CODE_CACHE = "cons.code_cache"
    const val TEMP_FILE = "cons.temp_file"
    const val THUMBNAIL = "cons.thumbnail"
    const val EMPTY_DIR = "cons.empty_dir"
    const val CRASH_LOG = "cons.crash_log"
    // ---- L2 ----
    const val CORPSE = "cons.corpse"
    const val CHAT_CACHE = "cons.chat_cache"
    const val SYSTEM_LOG = "cons.system_log"
    // ---- REPORT_ONLY ----
    const val DOWNLOAD_LARGE = "cons.download_large"
    const val OLD_APK = "cons.old_apk"
    const val LARGE_OLD = "cons.large_old"
    // ---- L3 / 兜底 ----
    const val L3_FORBIDDEN = "cons.l3_forbidden"
    const val UNKNOWN = "cons.unknown"

    private val entries: List<CleanConsequence> = listOf(
        CleanConsequence(
            id = APP_CACHE,
            summary = "首次启动变慢、资源重新下载；不影响登录态 / 设置 / 聊天记录；不可恢复但会自动重建",
            detail = "仅清除应用外部 cache 目录中的可再生成缓存。应用下次启动会重新下载或生成资源，可能短暂停顿；账号、设置、数据库、聊天文字记录均不受影响。",
            impact = "仅影响该应用的临时缓存，不影响用户数据",
            riskLevel = RiskLevel.L1_SAFE
        ),
        CleanConsequence(
            id = CODE_CACHE,
            summary = "代码 / 优化缓存被清，首次启动重新生成略慢；不影响账号与数据；不可恢复但会自动重建",
            detail = "code_cache 存放运行时编译产物，删除后系统会按需重建。不会触碰登录态、设置或任何持久化数据。",
            impact = "仅影响该应用的运行时优化缓存",
            riskLevel = RiskLevel.L1_SAFE
        ),
        CleanConsequence(
            id = TEMP_FILE,
            summary = "临时文件被清，相关应用可能需要重新生成；不影响正式数据；不可恢复但会自动重建",
            detail = ".tmp / 临时下载片段属于可丢弃文件。删除后若应用仍需要，会重新创建；不会删除正式文档或媒体。",
            impact = "仅临时文件，不含正式数据",
            riskLevel = RiskLevel.L1_SAFE
        ),
        CleanConsequence(
            id = THUMBNAIL,
            summary = "缩略图被清，相册 / 文件管理器首次打开会重新生成略慢；原图不受影响；不可恢复但会自动重建",
            detail = "缩略图是原图的派生缓存，删除后系统会重新生成。原始照片、视频、文档不会被删除。",
            impact = "仅缩略图缓存，原图安全",
            riskLevel = RiskLevel.L1_SAFE
        ),
        CleanConsequence(
            id = EMPTY_DIR,
            summary = "空目录被移除，不含任何文件；应用需要时会自动重建；不可恢复但会自动重建",
            detail = "仅删除确认为空的目录。删除前逐项复核目录仍为空；不含任何文件内容。",
            impact = "仅空目录，无文件内容",
            riskLevel = RiskLevel.L1_SAFE
        ),
        CleanConsequence(
            id = CRASH_LOG,
            summary = "崩溃 / ANR 诊断日志被清，不影响应用正常运行与数据；不可恢复但会自动重建",
            detail = "tombstones / ANR traces 仅用于开发排错。删除后不影响应用功能与用户数据，后续崩溃会生成新日志。",
            impact = "仅诊断日志",
            riskLevel = RiskLevel.L1_SAFE
        ),
        // ---- L2 ----
        CleanConsequence(
            id = CORPSE,
            summary = "卸载残留可能含存档 / 导出文件，删除后可能永久丢失；应用若重新安装不会自动恢复这些文件；不可恢复",
            detail = "判定条件：包未安装 + mtime 超过 30 天 + 已知残留目录名 + 删除前重查。一旦删除，其中的存档、导出文件、离线资料不会自动找回。",
            impact = "可能包含不可再生的存档 / 导出文件",
            riskLevel = RiskLevel.L2_CONFIRM
        ),
        CleanConsequence(
            id = CHAT_CACHE,
            summary = "语音与附件缓存可能永久丢失、文件可能已过期；文字聊天记录不受影响；不可恢复",
            detail = "聊天应用的图片 / 语音 / 文件缓存被清后，若服务器文件已过期则无法重新下载；文字消息、联系人、账号数据不受影响。",
            impact = "语音 / 图片 / 附件缓存，文字记录安全",
            riskLevel = RiskLevel.L2_CONFIRM
        ),
        CleanConsequence(
            id = SYSTEM_LOG,
            summary = "系统日志被清，仅影响排错取证，不影响系统运行与用户数据；不可恢复但会自动重建",
            detail = "系统 / 应用运行日志仅用于诊断。删除后系统继续产生新日志，不影响任何用户数据或设置。",
            impact = "仅日志文本",
            riskLevel = RiskLevel.L2_CONFIRM
        ),
        // ---- REPORT_ONLY ----
        CleanConsequence(
            id = DOWNLOAD_LARGE,
            summary = "需重新下载，本 App 恒为仅报告，不自动删除；不可恢复（若你手动在系统文件管理器删除）",
            detail = "本应用只报告 Download 中的大文件路径与大小，绝不将其加入删除队列。如需清理请自行到系统文件管理器操作。",
            impact = "本 App 不产生任何删除动作",
            riskLevel = RiskLevel.L2_CONFIRM
        ),
        CleanConsequence(
            id = OLD_APK,
            summary = "旧安装包被删后需重新下载；本 App 恒为仅报告，不自动删除；不可恢复（若你手动删除）",
            detail = "旧 APK / 安装包只做报告，不进入删除队列。是否删除完全由用户自行决定。",
            impact = "本 App 不产生任何删除动作",
            riskLevel = RiskLevel.L2_CONFIRM
        ),
        CleanConsequence(
            id = LARGE_OLD,
            summary = "只做索引与路径展示，本 App 不会删除；是否清理完全由你自行判断",
            detail = "超过 30 天未修改且体积较大的文件单独汇总成索引，方便你判断哪些是不再需要的大文件。索引项永远不进入删除队列；相册 / 视频 / OBB 等受保护目录即使出现在索引里也标记为已禁止。",
            impact = "本 App 不产生任何删除动作，仅展示大小与路径",
            riskLevel = RiskLevel.L2_CONFIRM
        ),
        // ---- L3 / 兜底 ----
        CleanConsequence(
            id = L3_FORBIDDEN,
            summary = "L3 受保护路径已硬编码禁止，本 App 不会删除；拦截原因见「已禁止」徽标",
            detail = "/data/data、/system、DCIM/Pictures/Camera、Android/obb、databases、shared_prefs 与凭据路径在 App 侧与 UserService 侧双重校验，任何情况下不可勾选、不可删除。",
            impact = "无删除动作，仅展示拦截原因",
            riskLevel = RiskLevel.L3_FORBIDDEN
        ),
        CleanConsequence(
            id = UNKNOWN,
            summary = "未分类项默认保守禁止，不会删除；不可恢复",
            detail = "未命中任何已知规则的路径按最保守策略处理，不进入删除队列，避免误删。",
            impact = "无删除动作",
            riskLevel = RiskLevel.L3_FORBIDDEN
        )
    )

    private val byIdMap: Map<String, CleanConsequence> = entries.associateBy { it.id }

    fun byId(id: String): CleanConsequence = byIdMap[id] ?: byIdMap.getValue(UNKNOWN)

    fun has(id: String): Boolean = byIdMap.containsKey(id)

    fun all(): List<CleanConsequence> = entries.toList()

    /** 返回缺失 consequenceId 文案的规则 id 列表（空列表 = 全部覆盖）。 */
    fun missingFor(ruleIds: List<Pair<String, String>>): List<String> =
        ruleIds.filter { (_, consequenceId) -> !has(consequenceId) }.map { it.first }
}
