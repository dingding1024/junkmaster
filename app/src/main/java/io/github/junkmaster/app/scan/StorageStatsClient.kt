package io.github.junkmaster.app.scan

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager

/**
 * 用量访问权（PACKAGE_USAGE_STATS）读取他应用缓存大小。
 * 读不到（未授权 / 异常）时返回 0，绝不抛异常阻断扫描。
 */
class StorageStatsClient(private val context: Context) {

    fun queryCacheBytes(packageName: String): Long = try {
        val manager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
        val stats = manager.queryStatsForPackage(
            StorageManager.UUID_DEFAULT,
            packageName,
            Process.myUserHandle()
        )
        stats.cacheBytes
    } catch (t: Throwable) {
        0L
    }
}
