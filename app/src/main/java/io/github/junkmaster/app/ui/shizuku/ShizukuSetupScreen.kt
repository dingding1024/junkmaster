package io.github.junkmaster.app.ui.shizuku

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.R
import io.github.junkmaster.app.shizuku.ShizukuStatus
import io.github.junkmaster.app.ui.components.SubScreen

@Composable
fun ShizukuSetupScreen(navController: NavController) {
    val vm: ShizukuSetupViewModel = viewModel()
    val status = vm.status.collectAsState().value
    val lastBind = vm.lastBind.collectAsState().value
    val message = vm.message.collectAsState().value
    val trace = vm.diagnose.collectAsState().value
    val trimReport = vm.trimReport.collectAsState().value

    LaunchedEffect(Unit) { vm.refresh() }

    SubScreen(
        title = stringResource(R.string.shizuku_title),
        onBack = { navController.popBackStack() }
    ) {
        Text(
            text = stringResource(R.string.shizuku_status_label) + "：" + status.label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        if (status == ShizukuStatus.READY) {
            Text(
                text = stringResource(R.string.shizuku_ready_hint),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = stringResource(R.string.shizuku_fail_closed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        // 最近一次操作 / 绑定失败的明确原因（反馈第 5 条：不再静默）
        if (lastBind.reason.isNotBlank()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.shizuku_last_bind_title),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = (if (lastBind.started) "✓ " else "✗ ") + lastBind.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lastBind.started) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(stringResource(R.string.shizuku_setup_steps_title), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.shizuku_step1), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.shizuku_step2), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.shizuku_step3), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.shizuku_flyme), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.shizuku_adb_restart), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { vm.refresh() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shizuku_refresh))
            }
            Button(onClick = { vm.requestPermission() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shizuku_request_permission))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { vm.forceRebind() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shizuku_rebind))
            }
            Button(onClick = { vm.unbind() }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.shizuku_unbind))
            }
        }
        Button(onClick = { vm.runDiagnose() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.shizuku_diagnose))
        }
        Button(onClick = { vm.probeTrim() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_probe_trim))
        }

        if (trace.isNotBlank()) {
            Text(
                text = stringResource(R.string.shizuku_diagnose_result),
                style = MaterialTheme.typography.labelLarge
            )
            SelectionContainer {
                Text(trace, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (trimReport.isNotBlank()) {
            SelectionContainer {
                Text(trimReport, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
