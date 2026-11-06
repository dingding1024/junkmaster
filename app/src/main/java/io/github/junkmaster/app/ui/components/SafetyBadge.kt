package io.github.junkmaster.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.DeleteMode
import io.github.junkmaster.app.model.RiskLevel
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.ui.theme.CautionAmber
import io.github.junkmaster.app.ui.theme.ForbiddenRed
import io.github.junkmaster.app.ui.theme.SafeGreen

/** 「是否安全」徽标：每条扫描结果常显。L1 安全 / L2 需确认 / L3 已禁止 / 仅报告。 */
@Composable
fun SafetyBadge(item: ScanItem, modifier: Modifier = Modifier) {
    val (label, background) = when {
        item.deleteMode == DeleteMode.REPORT_ONLY ->
            stringResource(R.string.badge_report_only) to CautionAmber
        item.riskLevel == RiskLevel.L3_FORBIDDEN || item.deleteMode == DeleteMode.FORBIDDEN ->
            stringResource(R.string.badge_forbidden) to ForbiddenRed
        item.riskLevel == RiskLevel.L1_SAFE ->
            stringResource(R.string.badge_safe) to SafeGreen
        else ->
            stringResource(R.string.badge_caution) to CautionAmber
    }
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(6.dp),
        contentColor = Color.White
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
