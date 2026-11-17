package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = TerracottaSecondary,
    onSecondary = TerracottaOnSecondary,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = TerracottaOnContainer,
    tertiary = SlateTertiary,
    onTertiary = SlateOnTertiary,
    tertiaryContainer = SlateContainer,
    onTertiaryContainer = SlateOnTertiaryContainer,
    background = AlabasterBackground,
    onBackground = CharcoalInk,
    surface = CrispPaperWhite,
    onSurface = CharcoalInk,
    surfaceVariant = WarmStoneSurface,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLowest = CrispPaperWhite,
    surfaceContainerLow = AlabasterBackground,
    surfaceContainer = WarmStoneSurface,
    surfaceContainerHigh = WarmStoneHigh,
    outline = PaperOutline,
    outlineVariant = PaperOutlineVariant,
    error = DangerRed,
    onError = CrispPaperWhite,
    errorContainer = DangerContainer,
    onErrorContainer = OnDangerContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = DarkEmeraldOnPrimary,
    primaryContainer = DarkEmeraldContainer,
    onPrimaryContainer = DarkEmeraldOnContainer,
    secondary = DarkTerracottaSecondary,
    onSecondary = DarkTerracottaOnSecondary,
    secondaryContainer = DarkTerracottaContainer,
    onSecondaryContainer = DarkTerracottaOnContainer,
    tertiary = SlateContainer,
    onTertiary = SlateOnTertiaryContainer,
    background = DarkLedgerBackground,
    onBackground = DarkInk,
    surface = DarkLedgerSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkLedgerSurfaceHigh,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = DarkLedgerBackground,
    surfaceContainerLow = DarkLedgerSurface,
    surfaceContainer = DarkLedgerSurfaceHigh,
    surfaceContainerHigh = Color(0xFF2B3531),
    outline = DarkOutline,
    outlineVariant = Color(0xFF2B3431)
)

private val ShopShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun ShopBillTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ShopShapes,
        content = content
    )
}
