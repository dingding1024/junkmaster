package io.github.junkmaster.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * 把一套 [PaletteTone] 展开成 Material 3 ColorScheme（第七轮反馈第 1 条）。
 *
 * 与上一轮的区别：上一轮只覆盖 primary/tertiary 与卡片底色，其余仍是 M3 默认，
 * 于是卡片的 containerColor（surfaceContainerHighest 系列）依然是 M3 默认的
 * 紫灰色，整屏看起来只是「按键换色」。现在把 background / surface / 5 级容器色 /
 * 正文 / 描边 / 反向表面全部按配色派生，卡片与底板都随色板走。
 *
 * 容器色用 lerp 现算，不手写 6 套 × 7 个值：浅色从 surface 向 surfaceVariant 递进，
 * 深色从 background 向 surfaceVariant 递进，保证同一套配色内明暗层次单调。
 *
 * 唯一不随主题变的是**语义红**：安全相关的「禁止 / 不可恢复」必须始终是红的，
 * 只按明暗调整亮度保证对比度（浅色 ForbiddenRed，深色 DarkForbiddenRed）。
 */
private fun PaletteTone.toColorScheme(dark: Boolean): ColorScheme {
    val lowest = if (dark) background else surface
    val low = if (dark) lerp(background, surface, 0.55f) else lerp(surface, background, 0.55f)
    val container = if (dark) surface else lerp(surface, surfaceVariant, 0.35f)
    val high = if (dark) lerp(surface, surfaceVariant, 0.55f) else lerp(surface, surfaceVariant, 0.60f)
    val highest = if (dark) surfaceVariant else lerp(surface, surfaceVariant, 0.85f)
    val dim = if (dark) background else lerp(surface, onSurface, 0.08f)
    val bright = if (dark) lerp(surface, onSurface, 0.08f) else surface

    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = primary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            surfaceBright = bright,
            surfaceDim = dim,
            surfaceContainer = container,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            surfaceContainerLow = low,
            surfaceContainerLowest = lowest,
            inverseSurface = onSurface,
            inverseOnSurface = surface,
            error = DarkForbiddenRed,
            onError = Color(0xFF3B0505),
            errorContainer = Color(0xFF6B1414),
            onErrorContainer = Color(0xFFFFDAD6),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color(0xFF000000)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            inversePrimary = primary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = tertiary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = primary,
            surfaceBright = bright,
            surfaceDim = dim,
            surfaceContainer = container,
            surfaceContainerHigh = high,
            surfaceContainerHighest = highest,
            surfaceContainerLow = low,
            surfaceContainerLowest = lowest,
            inverseSurface = onSurface,
            inverseOnSurface = surface,
            error = ForbiddenRed,
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFFFDAD6),
            onErrorContainer = Color(0xFF410002),
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color(0xFF000000)
        )
    }
}

@Composable
fun JunkMasterTheme(
    palette: ThemePalette = ThemePalette.DEFAULT,
    mode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.AUTO -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = remember(palette, dark) { palette.tone(dark).toColorScheme(dark) }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}
