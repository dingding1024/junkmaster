package io.github.junkmaster.app.ui.result

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.R
import io.github.junkmaster.app.ui.components.ScreenFrame
import io.github.junkmaster.app.ui.formatBytes
import io.github.junkmaster.app.ui.nav.Routes

/**
 * 清理结果页（2026-11-05 第四轮真机反馈重写）。
 *
 * 旧版把「已删 67 项 / 0 B」写成「已删文件合计」，又把防误删的安全跳过写成
 * 「失败项 8」+ 红字「已中止（系统性异常）」，用户看到会理所当然地认为清理失败。
 * 现在严格区分：已清理项 / 释放空间 / 其中空目录 / 安全跳过 / 真实失败 / 安全拦截，
 * 并在释放 0 B 时明确解释「空目录不占空间」以及「缓存需要 Shizuku」。
 */
@Composable
fun CleanResultScreen(navController: NavController) {
    val vm: CleanResultViewModel = viewModel()
    val report = vm.report.collectAsState().value

    ScreenFrame(title = stringResource(R.string.clean_result_title)) {
        if (report == null) {
            Text(stringResource(R.string.scan_result_empty))
        } else {
            Text(
                text = stringResource(R.string.clean_result_deleted) + "：" +
                        report.deletedCount + " 项 / " + formatBytes(report.freedBytes),
                style = MaterialTheme.typography.titleMedium
            )
            if (report.emptyDirDeletedCount > 0) {
                Text(
                    text = stringResource(R.string.clean_result_empty_dirs) + "：" +
                            report.emptyDirDeletedCount + " 个",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                text = stringResource(R.string.clean_result_capacity) + "：" +
                        (report.capacityDelta?.let { formatBytes(it) } ?: "N/A"),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.clean_result_note),
                style = MaterialTheme.typography.bodySmall
            )

            // 释放 0 B 是「只清了空目录」的正常结果，必须解释清楚并给出深度清理入口
            if (report.freedBytes == 0L && report.deletedCount > 0) {
                Text(
                    text = stringResource(R.string.clean_result_only_empty_dirs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (report.freedBytes == 0L) {
                Text(
                    text = stringResource(R.string.clean_result_deep_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = { navController.navigate(Routes.SHIZUKU_SETUP) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.clean_result_go_shizuku))
                }
            }

            Text(
                text = stringResource(R.string.clean_result_batches) + "：" + report.batches,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.clean_result_failed) + "：" + report.failedCount,
                style = MaterialTheme.typography.bodySmall
            )
            if (report.failedCount > 0) {
                Text(
                    text = stringResource(R.string.clean_result_failed_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                report.failedReasons.forEach { reason ->
                    Text(
                        text = stringResource(R.string.clean_result_failed_reason) + "：" + reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            if (report.skippedCount > 0) {
                Text(
                    text = stringResource(R.string.clean_result_skipped) + "：" + report.skippedCount,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.clean_result_skipped_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                report.skippedReasons.forEach { reason ->
                    Text(
                        text = stringResource(R.string.clean_result_skipped_reason) + "：" + reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (report.guardRejectedCount > 0) {
                Text(
                    text = stringResource(R.string.clean_result_guard_rejected) + "：" +
                            report.guardRejectedCount,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.clean_result_guard_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                report.guardRejectReasons.forEach { reason ->
                    Text(
                        text = stringResource(R.string.clean_result_guard_reason) + "：" + reason,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (report.stoppedEarly) {
                Text(
                    text = stringResource(R.string.clean_result_stopped) + "：" + report.stopReason,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(stringResource(R.string.clean_result_done))
            }
        }

        Button(
            onClick = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.clean_result_back_home))
        }
    }
}
