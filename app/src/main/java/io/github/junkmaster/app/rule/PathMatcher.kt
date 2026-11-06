package io.github.junkmaster.app.rule

/**
 * 纯 JVM 路径匹配器。所有「已指定」的条件之间为 AND，单个条件内部为 OR / ALL。
 * 空 matcher（MATCH_NONE）永不命中，用于「由 Finder 按 category 直接构造」的规则。
 */
class PathMatcher(
    /** 这些子串必须全部出现在归一化路径中（AND） */
    private val allSubstrings: Set<String> = emptySet(),
    /** 路径分段中必须出现至少一个（OR） */
    private val anySegments: Set<String> = emptySet(),
    /** 文件名后缀命中任一即可（OR） */
    private val suffixes: Set<String> = emptySet(),
    /** 文件名精确等于任一即可（OR，均为小写） */
    private val exactNames: Set<String> = emptySet()
) {
    companion object {
        /** 永不命中：供 L3 / 卸载残留 / 空目录等按 category 构造的规则使用 */
        val MATCH_NONE = PathMatcher()
    }

    private val isEmpty: Boolean
        get() = allSubstrings.isEmpty() && anySegments.isEmpty() &&
                suffixes.isEmpty() && exactNames.isEmpty()

    fun matches(path: String): Boolean {
        if (isEmpty) return false
        val n = path.replace('\\', '/').lowercase()
        val name = n.substringAfterLast('/')
        val segments = n.split('/').filter { it.isNotEmpty() }.toSet()

        if (allSubstrings.isNotEmpty() && !allSubstrings.all { n.contains(it.lowercase()) }) return false
        if (anySegments.isNotEmpty() && anySegments.none { it.lowercase() in segments }) return false
        if (suffixes.isNotEmpty() && suffixes.none { name.endsWith(it.lowercase()) }) return false
        if (exactNames.isNotEmpty() && exactNames.none { name == it.lowercase() }) return false
        return true
    }
}
