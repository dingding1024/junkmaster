package io.github.junkmaster.app.ui.confirm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.shizuku.ShizukuStatus
import io.github.junkmaster.app.ui.AppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 深度确认 ViewModel：强制勾选「我已知晓删除不可恢复」+ Shizuku READY 才放行。 */
class DeepConfirmViewModel : ViewModel() {

    private val _acknowledged = MutableStateFlow(false)
    val acknowledged: StateFlow<Boolean> = _acknowledged

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    val items: List<ScanItem> get() = AppSession.pendingSelection

    fun setAcknowledged(value: Boolean) {
        _acknowledged.value = value
    }

    fun execute() {
        if (!_acknowledged.value) {
            _message.value = "need_check"
            return
        }
        _message.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            val gateway = AppServices.shizuku
            if (gateway.currentStatus() != ShizukuStatus.READY) {
                _message.value = "lost"
                return@launch
            }
            // fail-closed：清理过程中 Shizuku 掉线立即中止（isCancelled）
            val report = AppServices.deepCleanExecutor().execute(
                AppSession.pendingSelection,
                isCancelled = { gateway.currentStatus() != ShizukuStatus.READY }
            )
            AppSession.cleanReport = report
            AppServices.writeLastClean(report)
            _done.value = true
        }
    }
}
