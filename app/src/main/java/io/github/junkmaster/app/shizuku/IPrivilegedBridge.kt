package io.github.junkmaster.app.shizuku

/**
 * App 侧访问 UserService 的抽象桥接（接口隔离）。
 * ShizukuBridge 为正式实现，PorterBridge 为备选；两者都受 ShizukuGateway 的 fail-closed 约束。
 */
interface IPrivilegedBridge {
    val isAlive: Boolean
    fun probe(): ShellResult
    fun listCandidates(filterJson: String): List<String>
    fun deletePaths(paths: List<String>): ShellResult
    fun trimCaches(desiredBytes: Long): TrimResult
    fun clearCacheViaBinder(packageName: String): ShellResult
    fun queryCacheBytes(packageName: String): Long
    fun destroy()
}
