package io.github.junkmaster.app.scan

import android.content.Context
import android.content.pm.PackageManager

/** 已安装包集合（QUERY_ALL_PACKAGES）。供 CorpseFinder 判断「应用是否还在 / 可能回来」。 */
class PackageRepository(private val context: Context) {

    fun installedPackages(): Set<String> = try {
        context.packageManager
            .getInstalledApplications(PackageManager.GET_META_DATA)
            .map { it.packageName }
            .toSet()
    } catch (t: Throwable) {
        emptySet()
    }
}
