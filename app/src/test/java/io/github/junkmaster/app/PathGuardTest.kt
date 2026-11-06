package io.github.junkmaster.app

import io.github.junkmaster.app.clean.PathGuard
import io.github.junkmaster.app.clean.StorageRoots
import io.github.junkmaster.app.clean.SymlinkProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 单测：PathGuard 逃逸 / L3 拦截 / 白名单优先级 / Android 挂载链符号链接。
 *
 * 重点：桌面 JVM 上 /sdcard 不存在，Files.isSymbolicLink 恒为 false，所以必须用
 * FakeProbe 注入设备真实语义。老板 2026-11-05 真机反馈的 A3 阻断 bug 正是被这个
 * 语义差异掩盖的 —— 旧版 87 个用例全绿，真机上却 0 删除。
 */
class PathGuardTest {

    /** 模拟 Android 文件系统：links = 哪些目录是符号链接，reals = 链接解析后的真实路径。 */
    private class FakeProbe(
        links: Set<String>,
        reals: Map<String, String> = emptyMap()
    ) : SymlinkProbe {
        private val linkSet = links.map { StorageRoots.normalizeDir(it) }.toSet()
        private val realMap = reals.mapKeys { StorageRoots.normalizeDir(it.key) }

        override fun isSymbolicLink(path: String): Boolean =
            StorageRoots.normalizeDir(path) in linkSet

        override fun realPath(path: String): String? =
            realMap[StorageRoots.normalizeDir(path)]
    }

    private fun androidMountChain(links: Set<String> = emptySet(), reals: Map<String, String> = emptyMap()) =
        PathGuard.Context(
            probe = FakeProbe(
                links = setOf("/sdcard", "/storage/self/primary") + links,
                reals = mapOf(
                    "/sdcard" to "/storage/emulated/0",
                    "/storage/self/primary" to "/storage/emulated/0"
                ) + reals
            )
        )

    // ---------- 原有基础用例（默认 NIO probe） ----------

    @Test
    fun l1CachePathIsAllowed() {
        val r = PathGuard.verify("/sdcard/Android/data/com.example/cache/foo.tmp")
        assertTrue(r.reason, r.allowed)
        assertFalse(r.blockedByL3)
    }

    @Test
    fun l3DataDataIsBlocked() {
        val r = PathGuard.verify("/data/data/com.example/databases/msg.db")
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun l3ObbIsBlocked() {
        val r = PathGuard.verify("/sdcard/Android/obb/com.example/main.obb")
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun l3CameraIsBlocked() {
        val r = PathGuard.verify("/sdcard/DCIM/Camera/IMG_001.jpg")
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun l3SharedPrefsIsBlocked() {
        val r = PathGuard.verify("/sdcard/Android/data/com.example/shared_prefs/settings.xml")
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun l3SystemPartitionIsBlocked() {
        val r = PathGuard.verify("/system/app/Foo.apk")
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun dotDotEscapeIsBlocked() {
        val r = PathGuard.verify("/sdcard/../etc/passwd")
        assertFalse(r.allowed)
        assertTrue(r.reason.contains(".."))
    }

    @Test
    fun emptyPathIsBlocked() {
        assertFalse(PathGuard.verify("").allowed)
        assertFalse(PathGuard.verify("   ").allowed)
    }

    @Test
    fun whitelistDenyWinsOverAllow() {
        val ctx = PathGuard.Context(deniedSubstrings = setOf("/keep.tmp"))
        val r = PathGuard.verify("/sdcard/Android/data/com.example/cache/keep.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.reason.contains("白名单"))
    }

    @Test
    fun whitelistDenyWinsEvenForL1CacheDirectory() {
        val ctx = PathGuard.Context(deniedSubstrings = setOf("com.example/cache"))
        val r = PathGuard.verify("/sdcard/Android/data/com.example/cache", ctx)
        assertFalse(r.allowed)
    }

    // ---------- 真机回归：Android 挂载链上的系统符号链接 ----------

    @Test
    fun sdcardSystemSymlinkParentsAreAllowed() {
        // 老板 A3 阻断的直接回归：/sdcard 与 /storage/self/primary 都是系统符号链接
        val ctx = androidMountChain()
        val cases = listOf(
            "/sdcard/Android/data/com.example/cache/foo.tmp",
            "/sdcard/Download/old.tmp",
            "/sdcard/.thumbnails/thumb.tmp",
            "/storage/emulated/0/Android/data/com.example/code_cache/x.tmp"
        )
        for (p in cases) {
            val r = PathGuard.verify(p, ctx)
            assertTrue("$p -> ${r.reason}", r.allowed)
        }
    }

    @Test
    fun multiUserEmulatedRootIsAllowed() {
        val ctx = PathGuard.Context(
            probe = FakeProbe(
                links = setOf("/storage/emulated/10", "/mnt/user/10/primary"),
                reals = mapOf(
                    "/storage/emulated/10" to "/data/media/10",
                    "/mnt/user/10/primary" to "/data/media/10"
                )
            )
        )
        assertTrue(
            PathGuard.verify("/storage/emulated/10/Android/data/com.x/cache/a.tmp", ctx).allowed
        )
    }

    @Test
    fun userDataSymlinkInParentIsRejected() {
        // 用户数据区自己出现的符号链接仍一律拒绝（防逃逸），不能因为修了 /sdcard 就放开
        val ctx = androidMountChain(links = setOf("/sdcard/Download/alias"))
        val r = PathGuard.verify("/sdcard/Download/alias/foo.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.reason, r.reason.contains("非系统符号链接"))
    }

    @Test
    fun targetItselfSymlinkIsRejected() {
        val ctx = androidMountChain(links = setOf("/sdcard/Download/fake.tmp"))
        val r = PathGuard.verify("/sdcard/Download/fake.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.reason, r.reason.contains("目标本身"))
    }

    @Test
    fun symlinkEscapeToPrivateDataIsRejected() {
        // 即使路径看起来是 /sdcard 下的 L1，解析后指向 /data/data 也必须拒绝
        val ctx = androidMountChain(
            reals = mapOf("/sdcard/Download/link.tmp" to "/data/data/com.x/databases/msg.db")
        )
        val r = PathGuard.verify("/sdcard/Download/link.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
        assertTrue(r.reason, r.reason.contains("L3"))
    }

    @Test
    fun systemMountChainPointingToForbiddenIsRejected() {
        // 系统挂载链被篡改指向私有分区，同样拒绝
        val ctx = PathGuard.Context(
            probe = FakeProbe(
                links = setOf("/sdcard"),
                reals = mapOf("/sdcard" to "/data/data/evil")
            )
        )
        val r = PathGuard.verify("/sdcard/Android/data/com.x/cache/a.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun l3StillBlockedWhenParentsAreSystemSymlinks() {
        val ctx = androidMountChain()
        val r = PathGuard.verify("/sdcard/DCIM/Camera/IMG_001.jpg", ctx)
        assertFalse(r.allowed)
        assertTrue(r.blockedByL3)
    }

    @Test
    fun dotDotStillBlockedWhenParentsAreSystemSymlinks() {
        val ctx = androidMountChain()
        val r = PathGuard.verify("/sdcard/../data/data/com.x/msg.db", ctx)
        assertFalse(r.allowed)
        assertTrue(r.reason.contains(".."))
    }

    @Test
    fun whitelistStillWinsWhenParentsAreSystemSymlinks() {
        val ctx = androidMountChain().copy(deniedSubstrings = setOf("/keep.tmp"))
        val r = PathGuard.verify("/sdcard/Download/keep.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.reason.contains("白名单"))
    }

    // ---------- StorageRoots 白名单边界 ----------

    @Test
    fun storageRootsOnlyCoversSystemMountChain() {
        assertTrue(StorageRoots.isSystemMountChain("/sdcard"))
        assertTrue(StorageRoots.isSystemMountChain("/storage/self/primary"))
        assertTrue(StorageRoots.isSystemMountChain("/storage/emulated/10"))
        assertTrue(StorageRoots.isSystemMountChain("/mnt/user/0/primary"))
        assertTrue(StorageRoots.isSystemMountChain("/storage"))
        assertTrue(StorageRoots.isSystemMountChain("/"))
        assertFalse("/sdcard/Download 不能被当成系统挂载链", StorageRoots.isSystemMountChain("/sdcard/Download"))
        assertFalse(StorageRoots.isSystemMountChain("/sdcard/Android/data/com.x/cache"))
        assertFalse(StorageRoots.isSystemMountChain("/data/data"))
        assertEquals("/sdcard/download", StorageRoots.normalizeDir("\\sdcard\\Download\\"))
    }

    // ---------- 第三轮真机回归：结构目录 / 厂商自定义挂载别名 ----------

    @Test
    fun structuralStorageDirectoriesAreBlockedForDirectoryTargets() {
        val ctx = androidMountChain()
        val cases = listOf(
            "/sdcard",
            "/sdcard/Android",
            "/sdcard/Android/data",
            "/sdcard/Android/obb",
            "/sdcard/Android/media",
            "/sdcard/DCIM",
            "/storage/emulated/0",
            "/storage/emulated/0/Download"
        )
        for (p in cases) {
            val r = PathGuard.verify(p, ctx, targetIsDirectory = true)
            assertFalse("$p -> ${r.reason}", r.allowed)
            assertTrue("$p -> ${r.reason}", r.reason.contains("结构目录"))
        }
    }

    @Test
    fun structuralCheckDoesNotBlockOrdinaryFilesOrDeepCacheDirs() {
        val ctx = androidMountChain()
        assertTrue(
            PathGuard.verify("/sdcard/Download/foo.tmp", ctx).allowed
        )
        val deep = PathGuard.verify(
            "/sdcard/Android/data/com.x/cache", ctx, targetIsDirectory = true
        )
        assertTrue(deep.reason, deep.allowed)
    }

    @Test
    fun structuralStorageDirBoundaries() {
        assertTrue(StorageRoots.isStructuralStorageDir("/sdcard/Android/data"))
        assertTrue(StorageRoots.isStructuralStorageDir("/storage/emulated/0"))
        assertTrue(StorageRoots.isStructuralStorageDir("/sdcard/DCIM"))
        assertFalse(StorageRoots.isStructuralStorageDir("/sdcard/Android/data/com.x/cache"))
        assertFalse(StorageRoots.isStructuralStorageDir("/sdcard/Download/old.tmp"))
        assertFalse(StorageRoots.isStructuralStorageDir("/data/data/com.x"))
        assertEquals("android/data", StorageRoots.relativeToStorageRoot("/sdcard/Android/data"))
    }

    @Test
    fun romSpecificMountAliasResolvedInsideMountPointsIsAllowed() {
        // 魅族 / 其它 ROM 可能在 /mnt 下增加命名不同的挂载别名。
        // 只要链接解析目标仍在系统挂载点内且非 L3，就按系统挂载链放行。
        val ctx = PathGuard.Context(
            probe = FakeProbe(
                links = setOf("/sdcard", "/mnt/meizu/alias"),
                reals = mapOf(
                    "/sdcard" to "/storage/emulated/0",
                    "/mnt/meizu/alias" to "/mnt/pass_through/0"
                )
            )
        )
        val r = PathGuard.verify("/mnt/meizu/alias/Android/data/com.x/cache/a.tmp", ctx)
        assertTrue(r.reason, r.allowed)
    }

    @Test
    fun userDataSymlinkResolvingOutsideMountPointsIsRejected() {
        val ctx = androidMountChain(
            links = setOf("/sdcard/Download/alias"),
            reals = mapOf("/sdcard/Download/alias" to "/data/misc/evil")
        )
        val r = PathGuard.verify("/sdcard/Download/alias/foo.tmp", ctx)
        assertFalse(r.allowed)
        assertTrue(r.reason, r.reason.contains("非系统符号链接"))
    }
}
