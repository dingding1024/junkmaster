package io.github.junkmaster.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.diag.DiagnosticsReportBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 测试诊断页：报告只在内存 StateFlow，不落公共目录、不自动上传。 */
class DiagnosticsViewModel : ViewModel() {

    private val _report = MutableStateFlow("")
    val report: StateFlow<String> = _report

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _note = MutableStateFlow("")
    val note: StateFlow<String> = _note

    fun onNoteChange(value: String) {
        _note.value = value
    }

    fun buildReport() {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val text = try {
                DiagnosticsReportBuilder(AppServices.applicationContext()).build(_note.value)
            } catch (t: Throwable) {
                "诊断报告生成失败：" + t.javaClass.simpleName + "\n" + (t.message ?: "")
            }
            _report.value = text
            _busy.value = false
        }
    }
}
