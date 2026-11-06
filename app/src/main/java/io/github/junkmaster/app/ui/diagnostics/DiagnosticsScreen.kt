package io.github.junkmaster.app.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.BuildConfig
import io.github.junkmaster.app.R
import io.github.junkmaster.app.ui.components.SubScreen

/**
 * 测试诊断页：一键汇总脱敏报告，支持屏显 / 复制 / 分享。
 * 只用 Intent.EXTRA_TEXT 纯文本，不需要 FileProvider、不产生 content URI、
 * 不改 Manifest、不受 scoped storage 影响、本 App 无需 INTERNET。
 */
@Composable
fun DiagnosticsScreen(navController: NavController) {
    val vm: DiagnosticsViewModel = viewModel()
    val report = vm.report.collectAsState().value
    val busy = vm.busy.collectAsState().value
    val note = vm.note.collectAsState().value
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    SubScreen(
        title = stringResource(R.string.diag_title),
        onBack = { navController.popBackStack() }
    ) {
        Text(
            text = stringResource(R.string.diag_privacy_hint),
            style = MaterialTheme.typography.bodySmall
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = stringResource(R.string.diag_section_report),
                    style = MaterialTheme.typography.labelLarge
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = if (report.isBlank()) {
                                stringResource(R.string.diag_empty)
                            } else {
                                report
                            },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 反馈第 2 条：原来三个按钮等宽并排，「一键生成报告」在窄屏上会折成两行、
        // 三个按钮高度还不一致。改成主操作独占一行、次操作两等分，不会再换行。
        Button(
            onClick = { vm.buildReport() },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (busy) stringResource(R.string.diag_generating)
                else stringResource(R.string.diag_generate)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (report.isNotBlank()) {
                        clipboard.setText(AnnotatedString(report))
                    }
                },
                enabled = report.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.diag_copy))
            }
            Button(
                onClick = {
                    if (report.isNotBlank()) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                context.getString(R.string.diag_share_subject) +
                                        " " + BuildConfig.VERSION_NAME +
                                        " (" + BuildConfig.VERSION_CODE + ")"
                            )
                        }
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    send,
                                    context.getString(R.string.diag_share_chooser)
                                )
                            )
                        }
                    }
                },
                enabled = report.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.diag_share))
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { vm.onNoteChange(it) },
            label = { Text(stringResource(R.string.diag_note_label)) },
            placeholder = { Text(stringResource(R.string.diag_note_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 5
        )
    }
}
