package io.github.junkmaster.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.ui.formatBytes

/**
 * 单条扫描结果卡：安全徽标 + 后果卡 + 完整路径 + 大小 + 拦截原因 + 勾选框。
 * L3 / REPORT_ONLY 不渲染勾选框（硬编码不可勾选）。
 */
@Composable
fun ScanItemCard(
    item: ScanItem,
    onToggle: ((ScanItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val checkable = onToggle != null && item.deleteMode.canDelete && item.riskLevel.isDeletable
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checkable) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { onToggle?.invoke(item) }
                    )
                }
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                SafetyBadge(item)
            }
            Text(
                text = stringResource(R.string.common_path) + "：" + item.path,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.common_size) + "：" + formatBytes(item.sizeBytes),
                style = MaterialTheme.typography.bodySmall
            )
            if (item.reason.isNotBlank()) {
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            // 深度项明确标注「需 Shizuku」，避免用户以为基础模式也能删
            if (item.requiresShizuku || item.riskLevel == RiskLevel.L2_CONFIRM) {
                Text(
                    text = stringResource(R.string.scan_result_need_shizuku_item),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            ConsequenceCard(item)
        }
    }
}
