package io.github.junkmaster.app.ui.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.scan.DeepDenyReason
import io.github.junkmaster.app.scan.DeepGateResult
import io.github.junkmaster.app.scan.ItemGroup
import io.github.junkmaster.app.scan.ItemGrouping
import io.github.junkmaster.app.shizuku.ShizukuStatus
import io.github.junkmaster.app.ui.components.ItemGroupCard
import io.github.junkmaster.app.ui.components.PathDetailDialog
import io.github.junkmaster.app.ui.components.ScreenFrame
import io.github.junkmaster.app.ui.formatBytes
import io.github.junkmaster.app.ui.nav.Routes

/**
 * 扫描结果页（第六轮反馈第 3、4 条重做）。
 *
 * 原实现四段平铺 + 每项重复后果卡：1856 项要滑几万像素，且同一句后果出现上千次。
 * 现在：
 *  1. 顶部按「是否需要特殊权限」分三个 Tab，默认只看第一个就能完成基础清理；
 *  2. 每个 Tab 内按规则聚合成组，**同类型后果只显示一次**；
 *  3. 组内条目折叠，展开后是紧凑单行（勾选 + 名称 + 大小 + 单行路径）；
 *  4. 组头支持整组勾选，不用逐项点。
 */
@Composable
fun ScanResultScreen(navController: NavController) {
    val vm: ScanResultViewModel = viewModel()
    val result = vm.result.collectAsState().value
    val done = vm.done.collectAsState().value
    val shizukuStatus = AppServices.shizuku.status.collectAsState().value

    var tab by rememberSaveable { mutableStateOf(0) }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    var deepDialog by remember { mutableStateOf<DeepGateResult?>(null) }
    var noticeDialog by remember { mutableStateOf<String?>(null) }
    // 第七轮反馈第 3 条：点任意条目查看实际路径 / 文件位置
    var detailItem by remember { mutableStateOf<ScanItem?>(null) }

    LaunchedEffect(done) {
        if (done) navController.navigate(Routes.CLEAN_RESULT)
    }

    val localItems = ItemGrouping.localItems(result.allItems)
    val deepItems = ItemGrouping.deepItems(result.allItems)
    val reportItems = ItemGrouping.reportItems(result.allItems)
    val current = when (tab) {
        0 -> localItems
        1 -> deepItems
        else -> reportItems
    }
    val groups = ItemGrouping.group(current)

    // scrollable = false：结果列表由本页的 LazyColumn 自己滚动并 weight(1f) 撑满，
    // 外层不能再套 verticalScroll（无限高约束 + weight 会抛异常）。
    ScreenFrame(title = stringResource(R.string.scan_result_title), scrollable = false) {
        // ---- 顶部总览 ----
        Text(
            text = stringResource(R.string.scan_result_total) + "：" + formatBytes(result.cleanableBytes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(
                R.string.scan_result_selected,
                vm.selectedCount(),
                formatBytes(vm.selectedBytes())
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ---- 三分区 Tab ----
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = {
                    Text(
                        text = stringResource(R.string.scan_tab_local) + " " + localItems.size,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = {
                    Text(
                        text = stringResource(R.string.scan_tab_deep) + " " + deepItems.size,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
            Tab(
                selected = tab == 2,
                onClick = { tab = 2 },
                text = {
                    Text(
                        text = stringResource(R.string.scan_tab_report) + " " + reportItems.size,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }

        // ---- 分区说明 ----
        Text(
            text = when (tab) {
                0 -> stringResource(R.string.scan_tab_local_hint)
                1 -> stringResource(R.string.scan_tab_deep_hint)
                else -> stringResource(R.string.scan_tab_report_hint)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (tab == 1 && shizukuStatus != ShizukuStatus.READY) {
            Text(
                text = stringResource(R.string.scan_result_need_shizuku) + "（" + shizukuStatus.label + "）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        // ---- 分组列表 ----
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (groups.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (tab == 0) R.string.scan_tab_local_empty else R.string.scan_result_empty
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            items(groups, key = { it.ruleId }) { group ->
                ItemGroupCard(
                    group = group,
                    expanded = expanded[group.ruleId] == true,
                    onToggleExpand = { expanded[group.ruleId] = expanded[group.ruleId] != true },
                    onToggleItem = if (group.checkable) {
                        { vm.toggle(it) }
                    } else {
                        null
                    },
                    onToggleGroup = if (group.checkable) {
                        { g: ItemGroup, checked: Boolean -> vm.toggleGroup(g, checked) }
                    } else {
                        null
                    },
                    onOpenPath = { detailItem = it }
                )
            }
        }

        // ---- 底部主操作 ----
        when (tab) {
            0 -> Button(
                onClick = {
                    if (vm.selectedCount() == 0) {
                        noticeDialog = "未勾选任何可清理项"
                    } else {
                        vm.cleanL1()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scan_result_clean))
            }

            1 -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = {
                        val gate = vm.prepareDeepConfirm()
                        if (gate.ok) {
                            navController.navigate(Routes.DEEP_CONFIRM)
                        } else {
                            // 关键修复：非 ok 必须弹窗说明原因，绝不静默
                            deepDialog = gate
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.scan_result_deep_confirm))
                }
                if (shizukuStatus != ShizukuStatus.READY) {
                    TextButton(
                        onClick = { navController.navigate(Routes.SHIZUKU_SETUP) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.home_nav_shizuku))
                    }
                }
            }
        }
    }

    // ---- 深度清理不可用：原因弹窗（反馈第 5 条） ----
    deepDialog?.let { gate ->
        AlertDialog(
            onDismissRequest = { deepDialog = null },
            title = { Text(stringResource(R.string.deep_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(reasonTextRes(gate.reason)))
                    if (gate.detail.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.deep_dialog_detail, gate.detail),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = stringResource(R.string.deep_dialog_noop_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    deepDialog = null
                    navController.navigate(Routes.SHIZUKU_SETUP)
                }) {
                    Text(stringResource(R.string.deep_dialog_go))
                }
            },
            dismissButton = {
                TextButton(onClick = { deepDialog = null }) {
                    Text(stringResource(R.string.common_know))
                }
            }
        )
    }

    noticeDialog?.let { text ->
        AlertDialog(
            onDismissRequest = { noticeDialog = null },
            title = { Text(stringResource(R.string.scan_result_title)) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = { noticeDialog = null }) {
                    Text(stringResource(R.string.common_know))
                }
            }
        )
    }

    // ---- 实际路径 / 文件位置详情（反馈第 3 条） ----
    detailItem?.let { item ->
        PathDetailDialog(item = item, onDismiss = { detailItem = null })
    }
}

private fun reasonTextRes(reason: DeepDenyReason): Int = when (reason) {
    DeepDenyReason.OK -> R.string.scan_result_empty
    DeepDenyReason.NO_SELECTION -> R.string.deep_deny_no_selection
    DeepDenyReason.SERVICE_NOT_RUNNING -> R.string.deep_deny_service_not_running
    DeepDenyReason.NEED_PERMISSION -> R.string.deep_deny_need_permission
    DeepDenyReason.DENIED -> R.string.deep_deny_denied
    DeepDenyReason.LOST -> R.string.deep_deny_lost
    DeepDenyReason.BINDING -> R.string.deep_deny_binding
    DeepDenyReason.BIND_FAILED -> R.string.deep_deny_bind_failed
}
