package io.github.junkmaster.app.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.R
import io.github.junkmaster.app.ui.components.SubScreen
import io.github.junkmaster.app.ui.nav.Routes

@Composable
fun AboutScreen(navController: NavController) {
    val vm: AboutViewModel = viewModel()

    SubScreen(
        title = stringResource(R.string.about_title),
        onBack = { navController.popBackStack() }
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InfoSection(
                    label = stringResource(R.string.about_version),
                    body = vm.version
                )
                HorizontalDivider()
                InfoSection(
                    label = stringResource(R.string.about_license),
                    body = stringResource(R.string.about_license_body)
                )
                HorizontalDivider()
                InfoSection(
                    label = stringResource(R.string.about_privacy),
                    body = stringResource(R.string.about_privacy_body)
                )
                HorizontalDivider()
                InfoSection(
                    label = stringResource(R.string.about_three_level),
                    body = stringResource(R.string.about_three_level_body)
                )
                HorizontalDivider()
                InfoSection(
                    label = stringResource(R.string.about_nostore),
                    body = stringResource(R.string.about_nostore_body)
                )
            }
        }

        Text(
            text = stringResource(R.string.irreversible_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )

        TextButton(onClick = { navController.navigate(Routes.DIAGNOSTICS) }) {
            Text(stringResource(R.string.diag_entry))
        }
    }
}

/** 小标题 + 正文，关于页与设置页共用同一节奏（标题粗、正文淡）。 */
@Composable
private fun InfoSection(label: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
