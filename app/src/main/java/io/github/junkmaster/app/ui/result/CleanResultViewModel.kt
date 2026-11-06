package io.github.junkmaster.app.ui.result

import androidx.lifecycle.ViewModel
import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.ui.AppSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 清理结果 ViewModel：展示已删合计、可用空间变化与中止原因。 */
class CleanResultViewModel : ViewModel() {
    val report: StateFlow<CleanReport?> = MutableStateFlow(AppSession.cleanReport)
}
