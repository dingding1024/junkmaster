package io.github.junkmaster.app

import io.github.junkmaster.app.scan.PathProbe
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「点开看实际路径」时的实时探测语义（第七轮反馈第 3 条）。
 *
 * 这里是上一轮真机事故的直接回归：`listFiles()` 返回 null 的骨架目录
 * （没有读取权限）**不是空目录**。当时把它当成空目录、默认勾选去删，
 * 结果 178 项里一大半删除失败。所以 `emptyDirectory` 必须同时要求
 * 「是目录 + 确实列举过（readable）+ 子项数为 0」。
 */
class PathProbeTest {

    @Test
    fun emptyDirectory_requiresReadableAndZeroChildren() {
        assertTrue(
            PathProbe(
                exists = true,
                isDirectory = true,
                sizeBytes = 0L,
                lastModifiedMs = 0L,
                childCount = 0,
                readable = true
            ).emptyDirectory
        )
        // 有子项 → 不是空目录
        assertFalse(
            PathProbe(true, true, 0L, 0L, 3, true).emptyDirectory
        )
    }

    @Test
    fun emptyDirectory_falseWhenListingFailed() {
        // childCount = -1 表示列不出来；即便 readable 被误写成 true 也不能算空
        assertFalse(
            PathProbe(true, true, 0L, 0L, -1, false).emptyDirectory
        )
        assertFalse(
            PathProbe(true, true, 0L, 0L, -1, true).emptyDirectory
        )
    }

    @Test
    fun emptyDirectory_falseForMissingPathOrFile() {
        assertFalse(PathProbe.MISSING.emptyDirectory)
        assertFalse(
            PathProbe(true, isDirectory = false, sizeBytes = 100L, lastModifiedMs = 0L, childCount = -1, readable = true)
                .emptyDirectory
        )
    }
}
