package io.github.junkmaster.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.rule.ConsequenceCatalog

/** 「删除后会有什么后果」中文说明卡：每条扫描结果常显，且恒显示「不可恢复」。 */
@Composable
fun ConsequenceCard(item: ScanItem, modifier: Modifier = Modifier) {
    val consequence = ConsequenceCatalog.byId(item.consequenceId)
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.consequence_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = consequence.summary,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = consequence.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // recoverable 恒为 false，因此「不可恢复」始终显示
            if (!consequence.recoverable) {
                Text(
                    text = stringResource(R.string.consequence_recoverable),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.consequence_impact) + "：" + consequence.impact,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
