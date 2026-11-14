package ua.alert.phone.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.alert.shared.DangerLevel

private val ExpressiveDarkFallbackScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF1F4287),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBAC6DF),
    onSecondary = Color(0xFF243043),
    secondaryContainer = Color(0xFF3A475B),
    onSecondaryContainer = Color(0xFFD6E2FC),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF101318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF101318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF43474F),
    onSurfaceVariant = Color(0xFFC3C6D0),
    outline = Color(0xFF8D919B),
    outlineVariant = Color(0xFF43474F),
    surfaceContainerLowest = Color(0xFF0B0E13),
    surfaceContainerLow = Color(0xFF181C22),
    surfaceContainer = Color(0xFF1D2026),
    surfaceContainerHigh = Color(0xFF272A31),
    surfaceContainerHighest = Color(0xFF32353C)
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private val ExpressiveLightFallbackScheme: ColorScheme = expressiveLightColorScheme()

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ExpressiveTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.15).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    )
)

data class SemanticAlertPalette(
    val container: Color,
    val onContainer: Color,
    val badgeContainer: Color,
    val onBadgeContainer: Color,
    val accent: Color
)

@Composable
fun rememberSemanticAlertPalette(
    dangerLevel: DangerLevel?,
    isDarkTheme: Boolean = isSystemInDarkTheme()
): SemanticAlertPalette {
    val scheme = MaterialTheme.colorScheme
    return when (dangerLevel) {
        DangerLevel.RED -> if (isDarkTheme) {
            SemanticAlertPalette(
                container = Color(0xFF5D0E12),
                onContainer = Color(0xFFFFDAD6),
                badgeContainer = Color(0xFF93000A),
                onBadgeContainer = Color(0xFFFFDAD6),
                accent = Color(0xFFFF5449)
            )
        } else {
            SemanticAlertPalette(
                container = Color(0xFFFFDAD6),
                onContainer = Color(0xFF410002),
                badgeContainer = Color(0xFFBA1A1A),
                onBadgeContainer = Color(0xFFFFFFFF),
                accent = Color(0xFFBA1A1A)
            )
        }

        DangerLevel.YELLOW -> if (isDarkTheme) {
            SemanticAlertPalette(
                container = Color(0xFF4D2B00),
                onContainer = Color(0xFFFFDDB3),
                badgeContainer = Color(0xFF7A4500),
                onBadgeContainer = Color(0xFFFFE6C7),
                accent = Color(0xFFFFB74D)
            )
        } else {
            SemanticAlertPalette(
                container = Color(0xFFFFE5C4),
                onContainer = Color(0xFF331B00),
                badgeContainer = Color(0xFFB45309),
                onBadgeContainer = Color(0xFFFFFFFF),
                accent = Color(0xFFB45309)
            )
        }

        DangerLevel.GREEN -> if (isDarkTheme) {
            SemanticAlertPalette(
                container = Color(0xFF003B24),
                onContainer = Color(0xFFAEF5D1),
                badgeContainer = Color(0xFF005B38),
                onBadgeContainer = Color(0xFFD1FAE5),
                accent = Color(0xFF34D399)
            )
        } else {
            SemanticAlertPalette(
                container = Color(0xFFCBF7DF),
                onContainer = Color(0xFF002112),
                badgeContainer = Color(0xFF047857),
                onBadgeContainer = Color(0xFFFFFFFF),
                accent = Color(0xFF047857)
            )
        }

        null -> SemanticAlertPalette(
            container = scheme.primaryContainer,
            onContainer = scheme.onPrimaryContainer,
            badgeContainer = scheme.primary,
            onBadgeContainer = scheme.onPrimary,
            accent = scheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AirAlertExpressiveTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> ExpressiveDarkFallbackScheme
        else -> ExpressiveLightFallbackScheme
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content
    )
}
