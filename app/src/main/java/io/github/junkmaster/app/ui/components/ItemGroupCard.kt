package io.github.junkmaster.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.ConsequenceCatalog
import io.github.junkmaster.app.scan.ItemGroup
import io.github.junkmaster.app.scan.LargeFileIndex
import io.github.junkmaster.app.ui.formatBytes
import io.github.junkmaster.app.ui.formatDateTime

/**
 * 一组同类扫描项（反馈第 3、4 条）。
 *
 * 设计要点：
 *  - **同类型后果说明只显示一次**，挂在组头下方，不再每项重复；
 *  - 组头常显「分类名 + 项数 + 合计大小 + 后果摘要一行」，不展开也能判断；
 *  - 组内条目默认折叠，展开后是紧凑行（勾选框 + 名称 + 大小 + 单行路径），
 *    避免 1000+ 项把页面拉成几万像素；
 *  - 组头可整组勾选 / 取消，避免逐项点几百次。
 */
@Composable
fun ItemGroupCard(
    group: ItemGroup,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleItem: ((ScanItem) -> Unit)? = null,
    onToggleGroup: ((ItemGroup, Boolean) -> Unit)? = null,
    /** 点条目查看「实际路径 / 文件位置」（第七轮反馈第 3 条）。 */
    onOpenPath: ((ScanItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val consequence = ConsequenceCatalog.byId(group.consequenceId)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            // ---- 组头 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "▾" else "▸",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.group_count_format, group.count, formatBytes(group.totalBytes)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (group.requiresShizuku) {
                    Text(
                        text = stringResource(R.string.group_needs_shizuku),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (group.checkable && onToggleGroup != null) {
                    TriStateCheckbox(
                        state = when {
                            group.allChecked -> ToggleableState.On
                            group.partiallyChecked -> ToggleableState.Indeterminate
                            else -> ToggleableState.Off
                        },
                        onClick = { onToggleGroup(group, !group.allChecked) }
                    )
                }
            }

            // ---- 后果摘要：折叠时也常显一行，展开后给完整说明（同类型只此一份） ----
            Text(
                text = consequence.summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
            )

            if (expanded) {
                Text(
                    text = consequence.detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                Text(
                    text = stringResource(R.string.consequence_impact) + "：" + consequence.impact,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
                if (!consequence.recoverable) {
                    Text(
                        text = stringResource(R.string.consequence_recoverable),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }
                if (group.items.any { it.requiresShizuku }) {
                    Text(
                        text = stringResource(R.string.scan_result_need_shizuku_item),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ---- 组内条目：紧凑行 ----
                group.items.forEach { item ->
                    ItemRow(item = item, onToggle = onToggleItem, onOpenPath = onOpenPath)
                }
            }
        }
    }
}

/**
 * 紧凑条目行：勾选框 + 名称 + 大小 + 单行路径 + 「›」。
 *
 * 整行可点 → 弹出「实际路径 / 文件位置」详情（第七轮反馈第 3 条）：
 * 路径在列表里必然被省略号截断，用户没法确认这到底是哪个目录，所以必须能点开看全。
 */
@Composable
private fun ItemRow(
    item: ScanItem,
    onToggle: ((ScanItem) -> Unit)?,
    onOpenPath: ((ScanItem) -> Unit)?
) {
    val checkable = onToggle != null && item.deleteMode.canDelete && item.riskLevel.isDeletable
    val rowModifier = if (onOpenPath != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onOpenPath(item) }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (checkable) {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onToggle?.invoke(item) }
            )
        } else {
            // 占位，保持不可勾选行与可勾选行左对齐
            Spacer(modifier = Modifier.width(40.dp))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatBytes(item.sizeBytes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onOpenPath != null) {
                    Text(
                        text = "›",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            // 索引类（大文件 / 旧 APK / Download）补一行「多久没动过」，
            // 这是判断「能不能删」的关键信息，光看大小不够。
            if (item.lastModifiedMs > 0L && item.category.isIndexLike) {
                Text(
                    // 大文件索引直接用「已 N 天未改动」，比「1 个月前」更利于判断能不能删
                    text = stringResource(
                        R.string.item_modified_days,
                        formatDateTime(item.lastModifiedMs),
                        LargeFileIndex.ageDays(item.lastModifiedMs, System.currentTimeMillis())
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.reason.isNotBlank()) {
                Text(
                    text = item.reason,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.deleteMode.canDelete) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
