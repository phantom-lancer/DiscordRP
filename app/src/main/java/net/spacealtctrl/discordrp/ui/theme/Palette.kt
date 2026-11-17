package net.spacealtctrl.discordrp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Immutable
data class Palette(
    val abyss: Color = Color(0xFF111214),
    val canvas: Color = Color(0xFF1E1F22),
    val panel: Color = Color(0xFF2B2D31),
    val raised: Color = Color(0xFF313338),
    val hover: Color = Color(0xFF383A40),
    val scrim: Color = Color(0xCC0B0B0D),

    val outline: Color = Color(0xFF3F4147),
    val outlineSoft: Color = Color(0xFF2E3035),

    val ink: Color = Color(0xFFF2F3F5),
    val inkFaint: Color = Color(0xFFB5BAC1),
    val inkGhost: Color = Color(0xFF80848E),
    val inkOnAccent: Color = Color(0xFF1F050F),

    val accent: Color = Color(0xFFFF7EB6),
    val accentDeep: Color = Color(0xFFE5679D),
    val accentBright: Color = Color(0xFFFFAFCC),
    val accentWash: Color = Color(0x33FF7EB6),
    val accentFaint: Color = Color(0x1AFF7EB6),

    val online: Color = Color(0xFF23A55A),
    val idle: Color = Color(0xFFF0B232),
    val dnd: Color = Color(0xFFF23F43),
    val invisible: Color = Color(0xFF80848E),

    val link: Color = Color(0xFF00A8FC),
    val danger: Color = Color(0xFFF23F43),
    val dangerWash: Color = Color(0x26F23F43),
    val warning: Color = Color(0xFFF0B232),
    val warningWash: Color = Color(0x26F0B232),
    val positive: Color = Color(0xFF23A55A),
    val positiveWash: Color = Color(0x2623A55A),
) {
    val sheen: Brush
        get() = Brush.verticalGradient(
            0f to Color(0x0FFFFFFF),
            0.5f to Color(0x04FFFFFF),
            1f to Color.Transparent,
        )

    val heroGlow: Brush
        get() = Brush.linearGradient(listOf(accentWash, accentFaint))

    companion object {
        fun from(scheme: ColorScheme): Palette = Palette(
            abyss = scheme.surfaceContainerLowest,
            canvas = scheme.background,
            panel = scheme.surfaceContainer,
            raised = scheme.surfaceContainerHigh,
            hover = scheme.surfaceContainerHighest,
            outline = scheme.outline,
            outlineSoft = scheme.outlineVariant,
            ink = scheme.onSurface,
            inkFaint = scheme.onSurfaceVariant,
            inkGhost = scheme.onSurfaceVariant.copy(alpha = 0.68f),
            inkOnAccent = scheme.onPrimary,
            accent = scheme.primary,
            accentDeep = scheme.primaryContainer,
            accentBright = scheme.secondary,
            accentWash = scheme.primary.copy(alpha = 0.20f),
            accentFaint = scheme.primary.copy(alpha = 0.10f),
        )
    }
}

val LocalPalette = staticCompositionLocalOf { Palette() }
