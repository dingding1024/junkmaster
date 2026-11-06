package io.github.junkmaster.app.clean

/**
 * Android 共享存储的「系统挂载链」白名单。纯 JVM，不依赖任何 android.* 类。
 *
 * 背景（2026-11-05 老板真机反馈 A3 阻断）：Android 上 `/sdcard` 本身就是一个符号链接
 * （→ `/storage/self/primary` → `/storage/emulated/0`），FUSE / sdcardfs 挂载链里还有若干
 * 系统别名。旧版 PathGuard 对「父路径链中出现任何符号链接」一律拒绝，于是 `/sdcard` 下
 * 的每一个扫描项在删除前复核阶段都被判「父路径含符号链接」，第一批第一项即中止，0 删除。
 * 桌面 JVM 上 `/sdcard` 根本不存在，所以纯 JVM 用例全绿也没能暴露这个语义差异。
 *
 * 2026-11-05 第三轮补充：真机上 `/sdcard/Android/data` 这类目录即使有「所有文件访问权」
 * 也可能 listFiles() 返回 null（无法列举）。旧版遍历器仍把它当作条目加入快照，
 * EmptyDirFinder 又因「快照里没有子项」把它误判为空目录并默认勾选，删除必然失败。
 * 因此这里额外提供「共享存储结构目录」判定：存储根、一级结构目录、Android/data|obb|media
 * 这类骨架目录一律不允许作为 L1 空目录被删除，即便扫描快照看起来是空的。
 */
object StorageRoots {

    /** 容量探测应指向共享存储，而不是根分区 `/`（根分区在 Android 上是只读 ramdisk）。 */
    const val DEFAULT_STORAGE_PATH: String = "/storage/emulated/0"

    /** 常见的共享存储别名（它们本身常常就是符号链接）。 */
    private val ALIASES: Set<String> = setOf(
        "/sdcard",
        "/mnt/sdcard",
        "/storage/self/primary",
        "/mnt/user/0/primary",
        "/mnt/androidwritable/0/primary",
        "/storage/emulated/0"
    )

    /** 挂载链上的中间目录，某些 ROM 也会把它们做成符号链接。 */
    private val PARENT_CHAIN: Set<String> = setOf(
        "/",
        "/storage",
        "/storage/self",
        "/storage/emulated",
        "/mnt",
        "/mnt/user",
        "/mnt/user/0",
        "/mnt/androidwritable",
        "/mnt/androidwritable/0",
        "/mnt/pass_through",
        "/mnt/runtime"
    )

    private val EMULATED_ROOT = Regex("^/storage/emulated/\\d+$")
    private val MULTI_USER_PRIMARY = Regex("^/mnt/user/\\d+/primary$")
    private val WRITABLE_PRIMARY = Regex("^/mnt/androidwritable/\\d+/primary$")
    private val PASS_THROUGH_ROOT = Regex("^/mnt/pass_through/\\d+$")

    /** 共享存储根的识别（用于把路径切成「根 + 相对段」）。 */
    private val ROOT_PATTERNS: List<Regex> = listOf(
        Regex("^/sdcard(?=/|$)"),
        Regex("^/mnt/sdcard(?=/|$)"),
        Regex("^/storage/self/primary(?=/|$)"),
        Regex("^/storage/emulated/\\d+(?=/|$)"),
        Regex("^/mnt/user/\\d+/primary(?=/|$)"),
        Regex("^/mnt/androidwritable/\\d+/primary(?=/|$)"),
        Regex("^/mnt/pass_through/\\d+(?=/|$)"),
        Regex("^/data/media/\\d+(?=/|$)")
    )

    /** 归一化目录字符串：小写、统一斜杠、去尾斜杠、保证以 / 开头。 */
    fun normalizeDir(raw: String): String {
        val n = raw.replace('\\', '/').lowercase().trim()
        val trimmed = if (n.length > 1) n.trimEnd('/') else n
        return if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    /**
     * 该目录若是符号链接，是否属于系统存储挂载链（可以安全放行）。
     * 注意：`/sdcard/Download/xxx` 这类用户数据区路径不在此集合内，仍会被拒绝。
     */
    fun isSystemMountChain(dir: String): Boolean {
        val n = normalizeDir(dir)
        return n in ALIASES ||
                n in PARENT_CHAIN ||
                EMULATED_ROOT.matches(n) ||
                MULTI_USER_PRIMARY.matches(n) ||
                WRITABLE_PRIMARY.matches(n) ||
                PASS_THROUGH_ROOT.matches(n)
    }

    /** 路径是否位于共享存储根之下；返回去掉根前缀后的相对段，否则 null。 */
    fun relativeToStorageRoot(path: String): String? {
        val n = normalizeDir(path)
        for (p in ROOT_PATTERNS) {
            val m = p.find(n) ?: continue
            return n.substring(m.value.length).trimStart('/')
        }
        return null
    }

    fun isUnderSystemStorageRoot(path: String): Boolean = relativeToStorageRoot(path) != null

    /**
     * 共享存储「结构目录」：删除它会破坏存储骨架或触及受保护区域，绝不能作为 L1 空目录清理。
     * 覆盖：存储根本身、一级目录（DCIM / Download / Android …）、Android 下的二级骨架目录
     * （data / obb / media）。更深层路径（如 /sdcard/Android/data/com.x/cache）不受影响，
     * 深度缓存仍可由 Shizuku UserService 处理。
     */
    fun isStructuralStorageDir(path: String): Boolean {
        val relative = relativeToStorageRoot(path) ?: return false
        val segments = relative.split('/').filter { it.isNotEmpty() }
        if (segments.isEmpty()) return true          // 存储根本身
        if (segments.size <= 1) return true          // 一级结构目录
        return segments[0] == "android" && segments.size <= 2
    }
}
