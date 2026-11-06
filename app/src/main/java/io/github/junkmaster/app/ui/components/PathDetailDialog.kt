package io.github.junkmaster.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.ConsequenceCatalog
import io.github.junkmaster.app.scan.FileSystemPathProbe
import io.github.junkmaster.app.scan.LargeFileIndex
import io.github.junkmaster.app.scan.PathProbe
import io.github.junkmaster.app.ui.FileOpener
import io.github.junkmaster.app.ui.formatAgeDays
import io.github.junkmaster.app.ui.formatBytes
import io.github.junkmaster.app.ui.formatDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 点开某一项后看到的「实际路径 / 文件位置」详情（第七轮反馈第 3 条）。
 *
 * 老板原话：「清理目录的时候增加可点选查看实际手机目录路径文件位置」。
 *
 * 这里解决三件事：
 *  1. 列表里路径只有一行、被省略号截断 → 这里给完整路径，可长按选取；
 *  2. 扫描是快照，用户点开时可能已经变了 → 当场重新探一次（存在 / 大小 / 子项数）；
 *  3. 想直接去看 → 一键复制路径，或交给系统文件管理器打开所在目录。
 *
 * 纯只读：探测只做 exists / list / length / lastModified，没有任何删除或写操作。
 */
@Composable
fun PathDetailDialog(item: ScanItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val probe by produceState<PathProbe?>(initialValue = null, key1 = item.path) {
        value = withContext(Dispatchers.IO) {
            runCatching { FileSystemPathProbe.probe(item.path) }.getOrNull()
        }
    }
    val consequence = ConsequenceCatalog.byId(item.consequenceId)
    val current = probe

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.path_detail_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                MetaRow(
                    label = stringResource(R.string.path_detail_type),
                    value = when {
                        current == null -> stringResource(R.string.path_detail_probing)
                        !current.exists -> stringResource(R.string.path_detail_type_unknown)
                        current.isDirectory -> stringResource(R.string.path_detail_type_dir)
                        else -> stringResource(R.string.path_detail_type_file)
                    }
                )
                MetaRow(
                    label = stringResource(R.string.path_detail_size),
                    value = formatBytes(current?.takeIf { it.exists }?.sizeBytes ?: item.sizeBytes)
                )
                if (item.lastModifiedMs > 0L) {
                    MetaRow(
                        label = stringResource(R.string.path_detail_modified),
                        value = formatDateTime(item.lastModifiedMs) + "（" +
                                formatAgeDays(
                                    LargeFileIndex.ageDays(
                                        item.lastModifiedMs,
                                        System.currentTimeMillis()
                                    )
                                ) + "）"
                    )
                }
                MetaRow(
                    label = stringResource(R.string.path_detail_state),
                    value = when {
                        current == null -> stringResource(R.string.path_detail_probing)
                        !current.exists -> stringResource(R.string.path_detail_missing)
                        current.isDirectory && !current.readable ->
                            stringResource(R.string.path_detail_unreadable)
                        current.isDirectory -> stringResource(
                            R.string.path_detail_children,
                            current.childCount
                        )
                        else -> stringResource(R.string.path_detail_exists)
                    },
                    highlight = current != null && !current.exists
                )
                MetaRow(
                    label = stringResource(R.string.path_detail_mode),
                    value = when {
                        item.riskLevel.code == "L3" -> stringResource(R.string.path_detail_mode_forbidden)
                        item.deleteMode.canDelete -> stringResource(R.string.path_detail_mode_deletable)
                        else -> stringResource(R.string.path_detail_mode_report)
                    }
                )

                Text(
                    text = stringResource(R.string.path_detail_full_path),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SelectionContainer {
                        Text(
                            text = item.path,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                if (item.reason.isNotBlank()) {
                    Text(
                        text = item.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.deleteMode.canDelete) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
                Text(
                    text = consequence.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(item.path))
                        Toast.makeText(
                            context,
                            context.getString(R.string.path_detail_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Text(stringResource(R.string.path_detail_copy))
                    }
                    TextButton(onClick = {
                        val isDir = current?.isDirectory ?: false
                        val opened = FileOpener.open(context, item.path, isDir)
                        if (!opened) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.path_detail_open_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }) {
                        Text(stringResource(R.string.path_detail_open))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
            }
        }
    )
}

@Composable
private fun MetaRow(label: String, value: String, highlight: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = if (highlight) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
