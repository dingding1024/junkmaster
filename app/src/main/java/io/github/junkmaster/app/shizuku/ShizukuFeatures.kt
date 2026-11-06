package io.github.junkmaster.app.shizuku

/** UserService probe() 的能力探测结果（纯 JVM，供 UI 状态卡展示）。 */
data class ShizukuFeatures(
    val sdkInt: Int = 0,
    val uid: Int = -1,
    val rawProbe: String = "",
    val trimCachesAvailable: Boolean = false,
    val binderClearCacheAvailable: Boolean = false,
    val shellCanDeleteAppDataCache: Boolean = false
)
