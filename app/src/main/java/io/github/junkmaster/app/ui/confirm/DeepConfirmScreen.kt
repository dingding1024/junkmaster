package io.github.junkmaster.app.ui.confirm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.R
import io.github.junkmaster.app.model.ScanItem
import io.github.junkmaster.app.ui.components.PathDetailDialog
import io.github.junkmaster.app.ui.components.ScanItemCard
import io.github.junkmaster.app.ui.components.ScreenFrame
import io.github.junkmaster.app.ui.nav.Routes

@Composable
fun DeepConfirmScreen(navController: NavController) {
    val vm: DeepConfirmViewModel = viewModel()
    val acknowledged = vm.acknowledged.collectAsState().value
    val message = vm.message.collectAsState().value
    val done = vm.done.collectAsState().value
    val items = vm.items
    // 反馈第 3 条：点条目查看实际路径 / 文件位置
    var detail by remember { mutableStateOf<ScanItem?>(null) }

    LaunchedEffect(done) {
        if (done) navController.navigate(Routes.CLEAN_RESULT)
    }

    // scrollable = false：本页内部的 LazyColumn 用 weight(1f) 撑满剩余高度，
    // 若外层再套 verticalScroll 就是「无限高约束 + weight」，运行期会崩。
    ScreenFrame(title = stringResource(R.string.deep_confirm_title), scrollable = false) {
        Text(
            text = stringResource(R.string.deep_confirm_warning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.deep_confirm_count, items.size),
            style = MaterialTheme.typography.bodyMedium
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { entry ->
                // 反馈第 3 条：深度确认页同样可以点开看实际路径，删除前先确认到底是哪个目录
                Box(modifier = Modifier.clickable { detail = entry }) {
                    ScanItemCard(entry)
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = acknowledged,
                onCheckedChange = { vm.setAcknowledged(it) }
            )
            Text(stringResource(R.string.deep_confirm_checkbox))
        }

        if (message == "need_check") {
            Text(
                text = stringResource(R.string.deep_confirm_need_check),
                color = MaterialTheme.colorScheme.error
            )
        }
        if (message == "lost") {
            Text(
                text = stringResource(R.string.deep_confirm_shizuku_lost),
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = { vm.execute() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.deep_confirm_proceed))
        }
    }

    detail?.let { entry ->
        PathDetailDialog(item = entry, onDismiss = { detail = null })
    }
}
