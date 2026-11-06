package io.github.junkmaster.app.shizuku

/**
 * 备选桥接（接口隔离，计划第 3 节：PorterBridge 不阻塞 MVP）。
 * 不执行任何删除，仅作为状态机 PORTER 分支的占位实现。
 */
class PorterBridge : IPrivilegedBridge {

    override val isAlive: Boolean = false

    override fun probe(): ShellResult =
        ShellResult(false, "PorterBridge 为接口隔离备选，未接入")

    override fun listCandidates(filterJson: String): List<String> = emptyList()

    override fun deletePaths(paths: List<String>): ShellResult =
        ShellResult(false, "PorterBridge 不执行删除")

    override fun trimCaches(desiredBytes: Long): TrimResult =
        TrimResult(false, "", 0L, "unavailable")

    override fun clearCacheViaBinder(packageName: String): ShellResult =
        ShellResult(false, "PorterBridge 不支持隐藏 API")

    override fun queryCacheBytes(packageName: String): Long = 0L

    override fun destroy() = Unit
}
