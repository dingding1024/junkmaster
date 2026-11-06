package io.github.junkmaster.app.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable

/**
 * 二级页面框架（设置 / 关于 / 诊断 / Shizuku）。
 *
 * 第七轮反馈第 2 条要求「下面三个界面的 UI 效果保持一致」，所以这里不再单独实现一套，
 * 直接委托给全应用唯一的 [ScreenFrame]：标题字号、返回按钮、内边距、滚动行为全部一致。
 * 保留这个名字只是为了让调用点读起来更清楚（这是一张二级页）。
 */
@Composable
fun SubScreen(
    title: String,
    onBack: () -> Unit,
    showBanner: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    ScreenFrame(
        title = title,
        onBack = onBack,
        showBanner = showBanner,
        content = content
    )
}
