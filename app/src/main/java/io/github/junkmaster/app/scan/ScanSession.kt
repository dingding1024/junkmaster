package io.github.junkmaster.app.scan

/** 一次只读扫描的会话状态（进度 / 取消）。纯 JVM。 */
class ScanSession(
    val id: String = "scan-" + System.currentTimeMillis(),
    val startedAtMs: Long = System.currentTimeMillis(),
    val roots: List<String> = DEFAULT_ROOTS
) {
    @Volatile var cancelled: Boolean = false
    @Volatile var scannedCount: Int = 0
    @Volatile var foundCount: Int = 0

    fun cancel() {
        cancelled = true
    }

    companion object {
        val DEFAULT_ROOTS: List<String> = listOf("/sdcard")
    }
}

data class ScanProgress(
    val phase: String,
    val scanned: Int,
    val found: Int,
    val percent: Int,
    val cancelled: Boolean
)
