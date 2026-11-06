package io.github.junkmaster.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import io.github.junkmaster.app.R
import io.github.junkmaster.app.ui.components.SubScreen
import io.github.junkmaster.app.ui.theme.ThemeMode
import io.github.junkmaster.app.ui.theme.ThemePalette

@Composable
fun SettingsScreen(navController: NavController) {
    val vm: SettingsViewModel = viewModel()
    val whitelist = vm.whitelist.collectAsState().value
    val probeTrim = vm.probeTrim.collectAsState().value
    val paletteId = vm.palette.collectAsState().value
    val modeId = vm.themeMode.collectAsState().value
    val textState = remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.load() }

    SubScreen(
        title = stringResource(R.string.settings_title),
        onBack = { navController.popBackStack() }
    ) {
        // ---- 外观：配色 + 明暗 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_theme_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_theme_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                PalettePicker(
                    currentId = paletteId,
                    currentDark = ThemeMode.fromId(modeId).let { mode ->
                        when (mode) {
                            ThemeMode.DARK -> true
                            ThemeMode.LIGHT -> false
                            // 跟随系统时按当前主题实际明暗预览，避免色卡与界面不一致
                            ThemeMode.AUTO -> isSystemInDarkTheme()
                        }
                    },
                    onPick = { vm.setPalette(it) }
                )

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.settings_mode_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_mode_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = ThemeMode.fromId(modeId) == mode
                        FilterChip(
                            selected = selected,
                            onClick = { vm.setThemeMode(mode.id) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        // ---- 白名单 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_whitelist_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_whitelist_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = textState.value,
                    onValueChange = { textState.value = it },
                    label = { Text(stringResource(R.string.common_path)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = {
                        vm.add(textState.value)
                        textState.value = ""
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.settings_add))
                }
                if (whitelist.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                whitelist.forEach { path ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = path,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { vm.remove(path) }) {
                            Text(stringResource(R.string.settings_remove))
                        }
                    }
                }
            }
        }

        // ---- 实测开关 ----
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.settings_probe_trim),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge
                    )
                    Switch(checked = probeTrim, onCheckedChange = { vm.setProbeTrim(it) })
                }
                Text(
                    text = stringResource(R.string.settings_probe_trim_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 配色选择器（第七轮反馈第 1 条）。
 *
 * 色卡改成「双层预览」：外圈是该配色的背景色，内圈是按键主色 ——
 * 一眼就能看出「深色背景 + 青瓷色按键」这种整体搭配，而不是只看到一个按钮色。
 */
@Composable
private fun PalettePicker(
    currentId: String,
    currentDark: Boolean,
    onPick: (String) -> Unit
) {
    val effective = ThemePalette.fromId(currentId)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.settings_theme_current, effective.label),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ThemePalette.entries.forEach { palette ->
                val selected = palette.id == effective.id
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onPick(palette.id) }
                        .padding(horizontal = 2.dp)
                ) {
                    val tone = palette.tone(currentDark)
                    Box(
                        modifier = Modifier
                            .size(if (selected) 44.dp else 40.dp)
                            .clip(CircleShape)
                            .background(tone.background)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (selected) 20.dp else 18.dp)
                                .clip(CircleShape)
                                .background(tone.primary)
                        )
                    }
                    Text(
                        text = palette.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
