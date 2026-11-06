package io.github.junkmaster.app.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.R
import io.github.junkmaster.app.shizuku.ShizukuStatus
import io.github.junkmaster.app.ui.AppSession
import io.github.junkmaster.app.ui.components.ScreenFrame
import io.github.junkmaster.app.ui.formatBytes
import io.github.junkmaster.app.ui.nav.Routes

@Composable
fun HomeScreen(navController: NavController) {
    val vm: HomeViewModel = viewModel()
    val context = LocalContext.current
    val capacity = vm.capacity.collectAsState().value
    val permissions = vm.permissions.collectAsState().value
    val shizukuStatus = AppServices.shizuku.status.collectAsState().value
    val lastBind = AppServices.shizuku.lastBind.collectAsState().value

    LaunchedEffect(Unit) { vm.refresh() }

    ScreenFrame(title = stringResource(R.string.home_title)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.home_capacity_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(
                        R.string.home_capacity_format,
                        formatBytes(capacity.usableBytes),
                        formatBytes(capacity.totalBytes)
                    ),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        // 产品主开关说明：不开 Shizuku 基础清扫照常可用，开了才解锁深度清理
        Text(
            text = stringResource(R.string.home_mode_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.home_readonly_hint),
            style = MaterialTheme.typography.bodySmall
        )

        // 基础模式权限引导：没有「所有文件访问权」时基础扫描拿不到结果
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.home_permission_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.home_permission_all_files) + "：" +
                            stringResource(
                                if (permissions.allFiles) R.string.home_permission_granted
                                else R.string.home_permission_missing
                            ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = stringResource(R.string.home_permission_usage) + "：" +
                            stringResource(
                                if (permissions.usageStats) R.string.home_permission_granted
                                else R.string.home_permission_missing
                            ),
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!permissions.allFiles && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Button(onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                            runCatching { context.startActivity(intent) }
                        }) {
                            Text(stringResource(R.string.home_permission_action))
                        }
                    }
                    if (!permissions.usageStats) {
                        Button(onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        }) {
                            Text(stringResource(R.string.home_permission_usage_action))
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                AppSession.reset()
                navController.navigate(Routes.SCAN_PROGRESS)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.home_l1_button))
        }
        Text(
            text = stringResource(R.string.home_l1_button_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.home_shizuku_card_title),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = stringResource(R.string.home_shizuku_status_prefix) + shizukuStatus.label,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.home_shizuku_unlock_hint),
                    style = MaterialTheme.typography.bodySmall
                )
                if (shizukuStatus != ShizukuStatus.READY) {
                    Text(
                        text = stringResource(R.string.home_shizuku_not_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    // 反馈第 5 条：把「为什么没连上」直接显示在首页，而不是让用户猜
                    if (lastBind.reason.isNotBlank()) {
                        Text(
                            text = stringResource(R.string.home_shizuku_last_reason, lastBind.reason),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = {
                        val intent = context.packageManager
                            .getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        val opened = if (intent != null) {
                            runCatching { context.startActivity(intent) }.isSuccess
                        } else {
                            false
                        }
                        if (!opened) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.home_open_shizuku_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }) {
                        Text(stringResource(R.string.home_open_shizuku))
                    }
                }
                TextButton(onClick = { navController.navigate(Routes.SHIZUKU_SETUP) }) {
                    Text(stringResource(R.string.home_nav_shizuku))
                }
            }
        }

        Text(
            text = stringResource(R.string.home_entries_title),
            style = MaterialTheme.typography.labelLarge
        )
        Text("· " + stringResource(R.string.home_entry_corpse), style = MaterialTheme.typography.bodySmall)
        Text("· " + stringResource(R.string.home_entry_temp), style = MaterialTheme.typography.bodySmall)
        Text("· " + stringResource(R.string.home_entry_chat), style = MaterialTheme.typography.bodySmall)
        Text("· " + stringResource(R.string.home_entry_download), style = MaterialTheme.typography.bodySmall)
        Text("· " + stringResource(R.string.home_entry_large), style = MaterialTheme.typography.bodySmall)

        // 第七轮反馈第 2 条：原来三个等宽按钮并排，「设置与白名单」6 个字在窄屏上换行，
        // 三个按钮高度还不一致。改成整行可点的入口列表：不换行、点区更大、观感统一。
        Card(modifier = Modifier.fillMaxWidth()) {
            Column {
                EntryRow(
                    text = stringResource(R.string.home_nav_settings),
                    onClick = { navController.navigate(Routes.SETTINGS) }
                )
                HorizontalDivider()
                EntryRow(
                    text = stringResource(R.string.home_nav_about),
                    onClick = { navController.navigate(Routes.ABOUT) }
                )
                HorizontalDivider()
                EntryRow(
                    text = stringResource(R.string.diag_entry),
                    onClick = { navController.navigate(Routes.DIAGNOSTICS) }
                )
            }
        }
    }
}

/** 首页底部入口行：整行可点 + 右侧箭头，文字不换行。 */
@Composable
private fun EntryRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
