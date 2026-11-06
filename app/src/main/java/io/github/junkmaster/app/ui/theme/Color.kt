package io.github.junkmaster.app.ui.theme

import androidx.compose.ui.graphics.Color

/** 品牌色与语义色。语义色（安全 / 警告 / 禁止）不随用户配色变化。 */
val BrandBlue = Color(0xFF1565C0)
val BrandBlueDark = Color(0xFF0D47A1)
val SafeGreen = Color(0xFF2E7D32)
val CautionAmber = Color(0xFFEF6C00)
val ForbiddenRed = Color(0xFFC62828)

/** 深色底上的禁止红：语义不变，只提亮以保证对比度（#C62828 在深色面上读不清）。 */
val DarkForbiddenRed = Color(0xFFFF6B6B)
