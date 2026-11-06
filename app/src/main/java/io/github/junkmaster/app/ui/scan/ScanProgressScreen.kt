package io.github.junkmaster.app.ui.scan

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.R
import io.github.junkmaster.app.ui.components.ScreenFrame
import io.github.junkmaster.app.ui.nav.Routes

@Composable
fun ScanProgressScreen(navController: NavController) {
    val vm: ScanProgressViewModel = viewModel()
    val progress = vm.progress.collectAsState().value
    val done = vm.done.collectAsState().value

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(done) {
        if (done) {
            navController.navigate(Routes.SCAN_RESULT) {
                popUpTo(Routes.SCAN_PROGRESS) { inclusive = true }
            }
        }
    }

    ScreenFrame(title = stringResource(R.string.scan_progress_title)) {
        Text(
            text = stringResource(R.string.scan_progress_readonly),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        if (!done) {
            CircularProgressIndicator()
        } else {
            Text(stringResource(R.string.scan_progress_go_result))
        }
        Text(
            text = stringResource(R.string.scan_progress_phase) + " " + progress.phase,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = stringResource(R.string.scan_progress_found, progress.found),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = progress.percent.toString() + "%",
            style = MaterialTheme.typography.headlineSmall
        )
        Button(
            onClick = { vm.cancel() },
            enabled = !done,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.scan_progress_cancel))
        }
        if (progress.cancelled) {
            Text(
                text = stringResource(R.string.scan_progress_cancelled),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
