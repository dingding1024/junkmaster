package io.github.junkmaster.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 一套完整的、同色系同明暗倾向的色板（第七轮反馈第 1 条）。
 *
 * 老板原话：「目前的主题色是按键色，我说的是整体界面主题色，如深色背景青瓷色按键配色这种搭配」。
 * 所以每套配色不再是「只换 primary」，而是给出**浅色 / 深色两份完整色板**：
 * 背景、卡片、卡片内底色、正文、次要文字、描边全部由配色派生，整屏观感统一。
 *
 * 设计约束：
 *  - 正文 onSurface 与 surface 对比度 >= 7:1；onSurfaceVariant 与 surface >= 4.5:1；
 *  - 主色与 onPrimary 对比度 >= 4.5:1（按钮文字必须清晰）；
 *  - 低饱和、中等明度，长时间看不刺眼；中国色系为主。
 */
data class PaletteTone(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color
)

/**
 * 可手动切换的配色方案。每套都自带浅色与深色两份色板，
 * 由 [ThemeMode] 决定当前用哪一份。
 */
enum class ThemePalette(
    val id: String,
    val label: String,
    val light: PaletteTone,
    val dark: PaletteTone
) {
    OCEAN(
        id = "ocean",
        label = "海天蓝",
        light = PaletteTone(
            primary = Color(0xFF1565C0),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD3E4FD),
            onPrimaryContainer = Color(0xFF0D3F7A),
            secondary = Color(0xFF2C6E8F),
            tertiary = Color(0xFF4A7BA7),
            background = Color(0xFFF5F9FE),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE3ECF8),
            onSurface = Color(0xFF0B1B2B),
            onSurfaceVariant = Color(0xFF40506B),
            outline = Color(0xFF7C8CA4),
            outlineVariant = Color(0xFFC6D4E6)
        ),
        dark = PaletteTone(
            primary = Color(0xFF7FB3F2),
            onPrimary = Color(0xFF06213F),
            primaryContainer = Color(0xFF14406E),
            onPrimaryContainer = Color(0xFFCFE2FB),
            secondary = Color(0xFF86BCD8),
            tertiary = Color(0xFF96B8D8),
            background = Color(0xFF0A1119),
            surface = Color(0xFF111C26),
            surfaceVariant = Color(0xFF1B2A38),
            onSurface = Color(0xFFE3ECF5),
            onSurfaceVariant = Color(0xFFA8BACD),
            outline = Color(0xFF6C809A),
            outlineVariant = Color(0xFF2C3D4F)
        )
    ),
    CELADON(
        id = "celadon",
        label = "青瓷",
        light = PaletteTone(
            primary = Color(0xFF3E6E68),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFD6E9E4),
            onPrimaryContainer = Color(0xFF12332F),
            secondary = Color(0xFF5C7A5F),
            tertiary = Color(0xFF9A7B4F),
            background = Color(0xFFF4FAF7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFDFEDE7),
            onSurface = Color(0xFF0E1D19),
            onSurfaceVariant = Color(0xFF3F5049),
            outline = Color(0xFF77908A),
            outlineVariant = Color(0xFFC3D8D1)
        ),
        dark = PaletteTone(
            primary = Color(0xFF7FC9BC),
            onPrimary = Color(0xFF00352E),
            primaryContainer = Color(0xFF1D4A43),
            onPrimaryContainer = Color(0xFFCFEAE3),
            secondary = Color(0xFF9DC29E),
            tertiary = Color(0xFFD3B183),
            background = Color(0xFF0A1512),
            surface = Color(0xFF10201C),
            surfaceVariant = Color(0xFF182E28),
            onSurface = Color(0xFFDDEFE9),
            onSurfaceVariant = Color(0xFF9FBBB2),
            outline = Color(0xFF6A857D),
            outlineVariant = Color(0xFF26403A)
        )
    ),
    ROUGE(
        id = "rouge",
        label = "胭脂",
        light = PaletteTone(
            primary = Color(0xFFA1374A),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF9DDE2),
            onPrimaryContainer = Color(0xFF5A1420),
            secondary = Color(0xFF8A5560),
            tertiary = Color(0xFFB07B52),
            background = Color(0xFFFDF6F7),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF3E1E5),
            onSurface = Color(0xFF26141A),
            onSurfaceVariant = Color(0xFF5C3B42),
            outline = Color(0xFF98707A),
            outlineVariant = Color(0xFFDCC0C7)
        ),
        dark = PaletteTone(
            primary = Color(0xFFF09AA5),
            onPrimary = Color(0xFF4B0B18),
            primaryContainer = Color(0xFF6B2230),
            onPrimaryContainer = Color(0xFFFBD6DC),
            secondary = Color(0xFFD9A3AC),
            tertiary = Color(0xFFDCB18A),
            background = Color(0xFF170B0E),
            surface = Color(0xFF211216),
            surfaceVariant = Color(0xFF312026),
            onSurface = Color(0xFFF4DFE3),
            onSurfaceVariant = Color(0xFFCBAAB2),
            outline = Color(0xFF95737C),
            outlineVariant = Color(0xFF40292F)
        )
    ),
    MOUNTAIN(
        id = "mountain",
        label = "黛山",
        light = PaletteTone(
            primary = Color(0xFF3C4A5E),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFDEE4ED),
            onPrimaryContainer = Color(0xFF1B2738),
            secondary = Color(0xFF6E7F5A),
            tertiary = Color(0xFF8A6F4C),
            background = Color(0xFFF6F8FB),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE4E9F1),
            onSurface = Color(0xFF111927),
            onSurfaceVariant = Color(0xFF414C5C),
            outline = Color(0xFF7A879C),
            outlineVariant = Color(0xFFC7D0DE)
        ),
        dark = PaletteTone(
            primary = Color(0xFFA9BEDB),
            onPrimary = Color(0xFF1B2A3D),
            primaryContainer = Color(0xFF2C3F58),
            onPrimaryContainer = Color(0xFFD6E2F2),
            secondary = Color(0xFFB4C59B),
            tertiary = Color(0xFFD2B489),
            background = Color(0xFF0B1017),
            surface = Color(0xFF121A24),
            surfaceVariant = Color(0xFF1C2733),
            onSurface = Color(0xFFE0E6EF),
            onSurfaceVariant = Color(0xFFA6B2C4),
            outline = Color(0xFF71809A),
            outlineVariant = Color(0xFF2B3847)
        )
    ),
    GILT(
        id = "gilt",
        label = "鎏金",
        light = PaletteTone(
            primary = Color(0xFF8E6512),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF8E9C5),
            onPrimaryContainer = Color(0xFF42300A),
            secondary = Color(0xFF4E4337),
            tertiary = Color(0xFF7A5A34),
            background = Color(0xFFFCF9F1),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1E7D2),
            onSurface = Color(0xFF231B0D),
            onSurfaceVariant = Color(0xFF574B3A),
            outline = Color(0xFF9A8A6C),
            outlineVariant = Color(0xFFDCCFB4)
        ),
        dark = PaletteTone(
            primary = Color(0xFFE9C36A),
            onPrimary = Color(0xFF3B2A00),
            primaryContainer = Color(0xFF5B420C),
            onPrimaryContainer = Color(0xFFFAE9BE),
            secondary = Color(0xFFCBBBA6),
            tertiary = Color(0xFFDDBB88),
            background = Color(0xFF14100A),
            surface = Color(0xFF1D1811),
            surfaceVariant = Color(0xFF2A2317),
            onSurface = Color(0xFFF0E7D6),
            onSurfaceVariant = Color(0xFFC6B79B),
            outline = Color(0xFF8D7C5C),
            outlineVariant = Color(0xFF3B3324)
        )
    ),
    SAKURA(
        id = "sakura",
        label = "樱粉",
        light = PaletteTone(
            primary = Color(0xFFA85273),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFFF9DEE8),
            onPrimaryContainer = Color(0xFF5C1730),
            secondary = Color(0xFF7E6472),
            tertiary = Color(0xFFB0866A),
            background = Color(0xFFFDF7F9),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF4E2E9),
            onSurface = Color(0xFF26141B),
            onSurfaceVariant = Color(0xFF57414B),
            outline = Color(0xFF9B7885),
            outlineVariant = Color(0xFFDEC4CE)
        ),
        dark = PaletteTone(
            primary = Color(0xFFF2A0BE),
            onPrimary = Color(0xFF4A0E23),
            primaryContainer = Color(0xFF6B2A42),
            onPrimaryContainer = Color(0xFFFBD9E5),
            secondary = Color(0xFFD6ACBB),
            tertiary = Color(0xFFDEB599),
            background = Color(0xFF170C11),
            surface = Color(0xFF21131A),
            surfaceVariant = Color(0xFF31212A),
            onSurface = Color(0xFFF5E1E9),
            onSurfaceVariant = Color(0xFFCCADB8),
            outline = Color(0xFF98757F),
            outlineVariant = Color(0xFF402C34)
        )
    );

    /**
     * 当前明暗倾向下应使用的色板。
     *
     * 注意参数名不能叫 `dark`：会和枚举属性 `dark` 同名，`if (dark) dark else light`
     * 里三处 `dark` 都指向那个 Boolean 参数，返回类型被推断成 `Any`，直接编译失败。
     */
    fun tone(isDark: Boolean): PaletteTone = if (isDark) dark else light

    /** 设置页色卡预览用的按键色（固定取浅色主色，深浅两态都用它做主色块）。 */
    val swatch: Color get() = light.primary

    companion object {
        val DEFAULT: ThemePalette = OCEAN

        /** 未知 / 空 id 一律回落默认，避免旧偏好导致启动异常。 */
        fun fromId(id: String?): ThemePalette =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * 浅色 / 深色 / 跟随系统（第七轮反馈第 1 条）。
 * 默认跟随系统：整屏底色与系统一致，不会出现「系统深色、应用惨白」的割裂。
 */
enum class ThemeMode(val id: String, val label: String) {
    AUTO("auto", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色");

    companion object {
        val DEFAULT: ThemeMode = AUTO

        fun fromId(id: String?): ThemeMode =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
