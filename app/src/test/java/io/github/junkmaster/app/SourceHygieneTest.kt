package io.github.junkmaster.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 纯 JVM 源码卫生断言（计划第 5 节）：
 *  - 源码 / Manifest / proguard 不得出现裸清数据命令字面量（带尾随空格、不带 --cache-only）
 *  - Manifest 不得声明 INTERNET / 清缓存签名权限 / 无障碍绑定权限
 *
 * 注意：被禁字符串在此测试里用拼接构造，避免测试源码自身出现该字面量。
 */
class SourceHygieneTest {

    private fun appMainCandidates(): List<File> = listOf(
        File("src/main/java"),
        File("../app/src/main/java"),
        File("app/src/main/java")
    )

    private fun collectSources(): List<File> {
        val out = mutableListOf<File>()
        appMainCandidates().filter { it.exists() }.forEach { root ->
            root.walkTopDown().filter { f ->
                f.isFile && (f.extension == "kt" || f.name == "AndroidManifest.xml" || f.name.endsWith(".pro"))
            }.forEach { out += it }
        }
        // 单独补 Manifest 与 proguard（可能不在 java 目录树下）
        listOf(
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml"),
            File("proguard-rules.pro"),
            File("../app/proguard-rules.pro")
        ).filter { it.exists() }.forEach { out += it }
        return out.distinctBy { it.absolutePath }
    }

    @Test
    fun noBareClearCommandLiteralInSources() {
        val forbidden = "pm" + " clear " // 运行时拼装，避免本文件出现裸字面量
        val files = collectSources()
        assumeTrue("源码目录不可达，跳过", files.isNotEmpty())
        files.forEach { f ->
            val text = f.readText()
            assertFalse("${f.path} 出现被禁的裸清数据命令字面量", text.contains(forbidden))
        }
    }

    @Test
    fun manifestDeclaresNoNetworkOrForbiddenPermissions() {
        val manifest = listOf(
            File("src/main/AndroidManifest.xml"),
            File("../app/src/main/AndroidManifest.xml")
        ).firstOrNull { it.exists() }
        assumeTrue("AndroidManifest.xml 不可达，跳过", manifest != null)
        val text = manifest!!.readText()
        val permissionLines = text.lines().filter { it.contains("uses-permission") }
        assertTrue("Manifest 声明了联网权限", permissionLines.none { it.contains("INTERNET") })
        assertTrue(
            "Manifest 声明了清缓存签名权限",
            permissionLines.none { it.contains("CLEAR_APP_CACHE") }
        )
        assertTrue(
            "Manifest 声明了无障碍服务绑定权限",
            permissionLines.none { it.contains("BIND_ACCESSIBILITY_SERVICE") }
        )
    }
}
