package io.github.junkmaster.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.junkmaster.app.R

/**
 * 全应用唯一的页面框架（第七轮反馈第 2 条）。
 *
 * 之前有两套：`ScreenFrame`（首页 / 结果页 / 确认页）与 `SubScreen`（设置 / 关于 / 诊断 / Shizuku），
 * 标题字号、内边距、返回按钮位置、能否滚动全不一样；首页在内容变多之后还会直接溢出屏幕，
 * 底部入口被挤掉。
 *
 * 现在统一为：**固定头部（标题 + 可选返回） + 常驻不可恢复提示 + 内容区**。
 * 头部不随内容滚动，返回按钮永远点得到。
 *
 * @param scrollable 内容区是否自带竖向滚动。
 *  - `true`（默认）：普通表单 / 说明型页面，内容再长也只是滚动；
 *  - `false`：页面自己管理滚动（内部有 `LazyColumn` 并依赖 `weight(1f)` 撑满剩余高度，
 *    如扫描结果页、深度确认页）。这两种约束不能同时存在，否则
 *    「无限高约束 + weight」会在运行期抛异常。
 */
@Composable
fun ScreenFrame(
    title: String,
    onBack: (() -> Unit)? = null,
    showBanner: Boolean = true,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- 固定头部：标题 + 返回。所有页面同一位置、同一字号 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                )
                if (onBack != null) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.common_back))
                    }
                }
            }

            if (showBanner) {
                IrreversibleBanner(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // ---- 内容区 ----
            val body = if (scrollable) {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            }
            Column(
                modifier = body.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}
