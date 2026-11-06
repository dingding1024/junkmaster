package io.github.junkmaster.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.junkmaster.app.AppServices
import io.github.junkmaster.app.ui.nav.AppNavHost
import io.github.junkmaster.app.ui.theme.JunkMasterTheme
import io.github.junkmaster.app.ui.theme.ThemeMode
import io.github.junkmaster.app.ui.theme.ThemePalette

/** 单 Activity + Compose（Material 3 中文）。 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppServices.init(applicationContext)
        setContent {
            // 配色 + 明暗模式由设置页写入 DataStore，这里响应式应用；读取是异步的，
            // 首帧用默认色，读到后自动重组，不会闪到未设色状态。
            val paletteId by AppServices.preferences.themePalette.collectAsState(initial = "")
            val modeId by AppServices.preferences.themeMode.collectAsState(initial = "")
            JunkMasterTheme(
                palette = ThemePalette.fromId(paletteId),
                mode = ThemeMode.fromId(modeId)
            ) {
                AppNavHost()
            }
        }
    }
}
