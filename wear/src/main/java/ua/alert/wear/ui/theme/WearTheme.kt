package ua.alert.wear.ui.theme

import android.graphics.Matrix
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalContext
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.dynamicColorScheme
import ua.alert.shared.DangerLevel

private val WearExpressiveFallbackScheme = ColorScheme(
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
    background = Color.Black,
    onBackground = Color(0xFFEAECEF),
    surfaceContainerLow = Color(0xFF14171D),
    surfaceContainer = Color(0xFF1B1F27),
    surfaceContainerHigh = Color(0xFF252A34),
    onSurface = Color(0xFFEAECEF),
    onSurfaceVariant = Color(0xFFBFC4CE),
    outline = Color(0xFF8B909A),
    outlineVariant = Color(0xFF3C414B)
)

data class WearSemanticPalette(
    val centerGlow: Color,
    val badgeColor: Color,
    val onBadge: Color,
    val titleColor: Color,
    val buttonContainer: Color,
    val buttonContent: Color
)

fun wearAlertPaletteFor(dangerLevel: DangerLevel): WearSemanticPalette {
    return when (dangerLevel) {
        DangerLevel.RED -> WearSemanticPalette(
            centerGlow = Color(0xFF5C0A0C),
            badgeColor = Color(0xFFB31B1B),
            onBadge = Color(0xFFFFFFFF),
            titleColor = Color(0xFFFFD8D6),
            buttonContainer = Color(0xFFFF5449),
            buttonContent = Color(0xFF3B0002)
        )
        DangerLevel.YELLOW -> WearSemanticPalette(
            centerGlow = Color(0xFF4D2B00),
            badgeColor = Color(0xFF9E5A00),
            onBadge = Color(0xFFFFFFFF),
            titleColor = Color(0xFFFFE5C2),
            buttonContainer = Color(0xFFFFB74D),
            buttonContent = Color(0xFF361C00)
        )
        DangerLevel.GREEN -> WearSemanticPalette(
            centerGlow = Color(0xFF003B23),
            badgeColor = Color(0xFF006D43),
            onBadge = Color(0xFFFFFFFF),
            titleColor = Color(0xFFC8F9DF),
            buttonContainer = Color(0xFF34D399),
            buttonContent = Color(0xFF002112)
        )
    }
}

@Composable
fun rememberWearAlertPalette(dangerLevel: DangerLevel): WearSemanticPalette {
    return remember(dangerLevel) { wearAlertPaletteFor(dangerLevel) }
}

@Composable
fun AirAlertWearTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val oledScheme = remember(context) {
        val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicColorScheme(context) ?: WearExpressiveFallbackScheme
        } else {
            WearExpressiveFallbackScheme
        }
        baseScheme.copy(background = Color.Black)
    }

    MaterialTheme(
        colorScheme = oledScheme,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}

@Composable
fun WearExpressivePolygonBadge(
    vertices: Int,
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val polygon = remember(vertices) {
        if (vertices <= 0) {
            RoundedPolygon.circle(numVertices = 12).normalized()
        } else {
            RoundedPolygon.star(
                numVerticesPerRadius = vertices,
                radius = 1f,
                innerRadius = 0.80f,
                rounding = CornerRounding(radius = 0.24f),
                innerRounding = CornerRounding(radius = 0.24f)
            ).normalized()
        }
    }

    Box(
        modifier = modifier.drawWithCache {
            val androidPath = android.graphics.Path()
            polygon.toPath(androidPath)
            val matrix = Matrix().apply { setScale(size.width, size.height) }
            androidPath.transform(matrix)
            val composePath = androidPath.asComposePath()
            onDrawBehind {
                drawPath(path = composePath, color = color)
            }
        },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
