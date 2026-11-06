package io.github.junkmaster.app.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.clean.CleanReport
import io.github.junkmaster.app.clean.combine
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.scan.DeepGate
import io.github.junkmaster.app.scan.DeepGateResult
import io.github.junkmaster.app.scan.ItemGroup
import io.github.junkmaster.app.scan.ScanResult
import io.github.junkmaster.app.scan.ScanResultAggregator
import io.github.junkmaster.app.shizuku.ShizukuStatus
import io.github.junkmaster.app.ui.AppSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 扫描结果 ViewModel：四分区、勾选、L1 一键直清、L2 进入深度确认。 */
class ScanResultViewModel : ViewModel() {

    private val _result = MutableStateFlow(AppSession.scanResult ?: ScanResult.empty())
    val result: StateFlow<ScanResult> = _result

    private val _hint = MutableStateFlow("")
    val hint: StateFlow<String> = _hint

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    fun toggle(item: ScanItem) {
        val updated = _result.value.allItems.map { current ->
            if (current.id == item.id && current.deleteMode.canDelete && current.riskLevel.isDeletable) {
                current.copy(checked = !current.checked)
            } else {
                current
            }
        }
        _result.value = ScanResultAggregator().aggregate(updated)
    }

    /** 整组勾选 / 取消（组内只有可删项会变，L3 与仅报告恒不勾）。 */
    fun toggleGroup(group: ItemGroup, checked: Boolean) {
        val ids = group.items
            .filter { it.deleteMode.canDelete && it.riskLevel.isDeletable }
            .map { it.id }
            .toSet()
        if (ids.isEmpty()) return
        val updated = _result.value.allItems.map { current ->
            if (current.id in ids) current.copy(checked = checked) else current
        }
        _result.value = ScanResultAggregator().aggregate(updated)
    }

    private fun selected(): List<ScanItem> =
        _result.value.allItems.filter { it.checked && it.deleteMode.canDelete }

    fun selectedCount(): Int = selected().size

    fun selectedBytes(): Long = selected().sumOf { it.sizeBytes }

    /**
     * L1 一键直清（无二次弹窗）。
     *
     * 双模式：基础项由 App 进程 NIO 直删（**不开 Shizuku 也能用**）；
     * 只有 requiresShizuku 的深度缓存项走 Shizuku UserService，并在 READY / 掉线时 fail-closed。
     * 两份报告合并后统一展示，避免深度项拖垮基础清理（失败即停只影响各自队列）。
     */
    fun cleanL1() {
        val l1 = _result.value.l1Items.filter { it.checked }
        if (l1.isEmpty()) {
            _hint.value = "empty"
            return
        }
        _hint.value = ""
        viewModelScope.launch(Dispatchers.IO) {
            val local = l1.filter { !it.requiresShizuku }
            val deep = l1.filter { it.requiresShizuku }
            var report: CleanReport? = null

            if (local.isNotEmpty()) {
                report = AppServices.cleanExecutor().execute(local)
            }

            if (deep.isNotEmpty()) {
                val gateway = AppServices.shizuku
                if (gateway.currentStatus() != ShizukuStatus.READY) {
                    // 扫描后 Shizuku 掉线：基础项照常清理，深度项如实提示、绝不谎报
                    _hint.value = "need_shizuku"
                } else {
                    val deepReport = AppServices.deepCleanExecutor().execute(
                        deep,
                        isCancelled = { gateway.currentStatus() != ShizukuStatus.READY }
                    )
                    report = report?.combine(deepReport) ?: deepReport
                }
            }

            if (report != null) {
                AppSession.cleanReport = report
                // 落盘无路径摘要，诊断页可自证「为什么是 0 删除」
                AppServices.writeLastClean(report)
                _done.value = true
            }
        }
    }

    /**
     * L2 深度确认前置：返回结构化判定。
     *
     * 非 ok 时**必须把原因交给 UI 弹窗**：原实现返回字符串，UI 只判断 == "ok"，
     * 于是「服务未运行 / 绑定失败 / 正在绑定 / 没勾选」这四种情况点按钮都毫无反应，
     * 用户无法判断到底卡在哪一步。
     */
    fun prepareDeepConfirm(): DeepGateResult {
        val l2 = _result.value.l2Items.filter { it.checked }
        val gate = DeepGate.decide(
            selectedCount = l2.size,
            status = AppServices.shizuku.currentStatus(),
            lastBind = AppServices.shizuku.lastBind.value
        )
        if (gate.ok) {
            AppSession.pendingSelection = l2
            AppSession.deepMode = true
            _hint.value = ""
        }
        return gate
    }
}
