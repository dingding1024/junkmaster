package io.github.junkmaster.app.ui.shizuku

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.shizuku.BindResult
import io.github.junkmaster.app.shizuku.ShizukuStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Shizuku 引导页 ViewModel：状态、授权、绑定 / 重绑 / 解绑、trim-caches 实测、一键诊断。 */
class ShizukuSetupViewModel : ViewModel() {

    val status: StateFlow<ShizukuStatus> get() = AppServices.shizuku.status

    /** 最近一次 bind() 的结构化结果（含失败原因），UI 必须显示出来。 */
    val lastBind: StateFlow<BindResult> get() = AppServices.shizuku.lastBind

    private val _trimReport = MutableStateFlow("")
    val trimReport: StateFlow<String> = _trimReport

    /** 最近一次按钮操作的反馈文案（替代静默无反应）。 */
    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    /** 一键诊断输出（可直接复制给开发者）。 */
    private val _diagnose = MutableStateFlow("")
    val diagnose: StateFlow<String> = _diagnose

    fun refresh() {
        AppServices.shizuku.refreshStatus()
        val s = AppServices.shizuku.currentStatus()
        _message.value = "已刷新：当前状态 " + s.label +
                (if (s == ShizukuStatus.READY) "" else "（" + AppServices.shizuku.lastBind.value.reason + "）")
    }

    fun requestPermission() {
        AppServices.shizuku.requestPermission()
        _message.value = "已请求 Shizuku 授权，请在系统弹窗中允许"
    }

    fun bind() {
        val r = AppServices.shizuku.bind()
        _message.value = r.reason
    }

    /** 强制重绑：清空陈旧连接后重新发起，用于「进程崩过但本地仍认为已连接」。 */
    fun forceRebind() {
        val r = AppServices.shizuku.forceRebind()
        _message.value = "重新绑定：" + r.reason
    }

    fun unbind() {
        AppServices.shizuku.unbind()
        _message.value = "已解绑深度服务（基础清扫不受影响）"
    }

    fun runDiagnose() {
        _diagnose.value = AppServices.shizuku.diagnose()
        _message.value = "诊断完成，结果见下方"
    }

    /** 实测 + 回退：异步执行，原始输出如实回显（不谎报释放量）。 */
    fun probeTrim() {
        viewModelScope.launch(Dispatchers.IO) {
            val before = AppServices.shizuku.currentStatus()
            val result = AppServices.shizuku.trimCachesWithFallback(64L * 1024L * 1024L)
            _trimReport.value = "status=" + before.name +
                    " ok=" + result.ok +
                    " strategy=" + result.strategy +
                    " freed=" + result.freedBytes + "\n" + result.rawOutput
        }
    }
}
