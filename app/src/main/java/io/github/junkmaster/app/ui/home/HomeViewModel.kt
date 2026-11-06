package io.github.junkmaster.app.ui.home

import androidx.lifecycle.ViewModel
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.clean.JvmCapacityProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CapacityInfo(val usableBytes: Long = 0L, val totalBytes: Long = 0L)

/** 基础清扫所需权限：所有文件访问权（必需）与用量访问权（读取各 App 缓存大小）。 */
data class PermissionState(
    val allFiles: Boolean = false,
    val usageStats: Boolean = false
) {
    val baseReady: Boolean get() = allFiles
}

/** 首页 ViewModel：容量环、基础权限状态、Shizuku 深度状态。 */
class HomeViewModel : ViewModel() {

    private val _capacity = MutableStateFlow(CapacityInfo())
    val capacity: StateFlow<CapacityInfo> = _capacity

    private val _permissions = MutableStateFlow(PermissionState())
    val permissions: StateFlow<PermissionState> = _permissions

    fun refresh() {
        val probe = JvmCapacityProbe()
        // 必须指向共享存储根：根分区 `/` 在 Android 上是只读 ramdisk，usableSpace 恒为 0。
        val root = AppServices.storageRoot()
        _capacity.value = CapacityInfo(probe.usableBytes(root), probe.totalBytes(root))
        val (allFiles, usage) = AppServices.basePermissions()
        _permissions.value = PermissionState(allFiles, usage)
        // 刷新 Shizuku 状态只影响深度模式；基础清理不依赖它
        AppServices.shizuku.refreshStatus()
    }
}
