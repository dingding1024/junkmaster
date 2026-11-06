package io.github.junkmaster.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.scan.ScanProgress
import io.github.junkmaster.app.scan.ScanSession
import io.github.junkmaster.app.scan.ScannerEngine
import io.github.junkmaster.app.ui.AppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 扫描进度 ViewModel：dry-run 只读、进度回调、可取消。 */
class ScanProgressViewModel : ViewModel() {

    private val _progress = MutableStateFlow(ScanProgress("", 0, 0, 0, false))
    val progress: StateFlow<ScanProgress> = _progress

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    private var session: ScanSession? = null

    fun start() {
        if (session != null) return
        val current = ScanSession()
        session = current
        viewModelScope.launch(Dispatchers.IO) {
            // 只读 dry-run：ScannerEngine 与所有 ItemSource 绝不删除任何文件
            val result = ScannerEngine(AppServices.sources()).scan(current) { p ->
                _progress.value = p
            }
            AppSession.scanResult = result
            // 落盘无路径的扫描分布摘要，诊断页可自证「基础扫描有没有产出有体积的项」
            AppServices.writeLastScan(result)
            _progress.value = _progress.value.copy(
                percent = 100,
                cancelled = result.cancelled
            )
            _done.value = true
        }
    }

    fun cancel() {
        session?.cancel()
        _progress.value = _progress.value.copy(cancelled = true)
    }
}
