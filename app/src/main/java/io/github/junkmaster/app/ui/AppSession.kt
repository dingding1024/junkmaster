package io.github.junkmaster.app.ui

import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.scan.ScanResult

/**
 * 进程内页面间共享状态（避免把 Parcelable / Serializable 大对象塞进导航参数）。
 * ViewModel 与 Screen 一一对应，但扫描结果 / 选择集 / 清理报告通过此会话传递。
 */
object AppSession {
    @Volatile var scanResult: ScanResult? = null
    @Volatile var cleanReport: CleanReport? = null
    @Volatile var pendingSelection: List<ScanItem> = emptyList()
    @Volatile var deepMode: Boolean = false

    fun reset() {
        scanResult = null
        cleanReport = null
        pendingSelection = emptyList()
        deepMode = false
    }
}
