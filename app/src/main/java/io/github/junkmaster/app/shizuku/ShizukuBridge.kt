package io.github.junkmaster.app.shizuku

/** 正式桥接：把 AIDL ICleanerUserService 包装成 IPrivilegedBridge，所有异常兜底为「失败」。 */
class ShizukuBridge(private val service: ICleanerUserService) : IPrivilegedBridge {

    override val isAlive: Boolean
        get() = try {
            service.asBinder().isBinderAlive
        } catch (t: Throwable) {
            false
        }

    override fun probe(): ShellResult =
        call(ShellResult(false, "binder 不可用")) { service.probe() }

    override fun listCandidates(filterJson: String): List<String> =
        call(emptyList()) { service.listCandidates(filterJson) }

    override fun deletePaths(paths: List<String>): ShellResult =
        call(ShellResult(false, "binder 不可用")) { service.deletePaths(paths) }

    override fun trimCaches(desiredBytes: Long): TrimResult =
        call(TrimResult(false, "binder 不可用", 0L, "unavailable")) { service.trimCaches(desiredBytes) }

    override fun clearCacheViaBinder(packageName: String): ShellResult =
        call(ShellResult(false, "binder 不可用")) { service.clearCacheViaBinder(packageName) }

    override fun queryCacheBytes(packageName: String): Long =
        call(0L) { service.queryCacheBytes(packageName) }

    override fun destroy() {
        try {
            service.destroy()
        } catch (t: Throwable) {
            // destroy 失败也继续走 unbind
        }
    }

    private fun <T> call(fallback: T, block: () -> T): T = try {
        block()
    } catch (t: Throwable) {
        fallback
    }
}
