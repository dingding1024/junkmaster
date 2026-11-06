package io.github.junkmaster.app.scan

/**
 * 双模式扫描源组装（纯 JVM，便于单测）。
 *
 * 老板补充的产品主开关：
 *  - 不开 Shizuku：只跑 base（App 进程可读的共享存储垃圾），基础安全清扫完全可用。
 *  - 开 Shizuku 且已绑定：额外加入 privileged 源（shell 进程列举 /sdcard/Android/data 应用缓存），
 *    解锁类似 SD Maid 的深度清理能力。
 *
 * privileged 源绝不在未 READY 时加入，避免产生 App 进程删不掉、导致「失败即停」的项。
 */
object ScanSources {
    fun build(
        base: List<ItemSource>,
        privileged: ItemSource?,
        shizukuReady: Boolean
    ): List<ItemSource> =
        if (shizukuReady && privileged != null) base + privileged else base
}
