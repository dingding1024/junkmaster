package io.github.junkmaster.app.scan

/**
 * L1 临时文件判定（纯 JVM）。供 TempFileSource / BasicCacheSource / UserService 侧共用。
 *
 * 2026-11-05 第四轮扩充：真机反馈基础模式扫出来全是空目录（0 B），
 * 补上浏览器 / 下载器 / 应用常用的下载片段后缀，让不开 Shizuku 的基础清扫也有实际产出。
 * 只匹配「扩展名」这一类明确可再生的临时文件；用户文档、媒体、APK 一律不碰。
 */
object TempFileFilter {

    val TEMP_SUFFIXES: List<String> = listOf(
        ".tmp", ".temp", ".part", ".partial", ".crdownload", ".download",
        ".tmpl", ".dctmp", "~"
    )

    fun isTemp(name: String): Boolean {
        val n = name.lowercase()
        return TEMP_SUFFIXES.any { n.endsWith(it) }
    }
}
