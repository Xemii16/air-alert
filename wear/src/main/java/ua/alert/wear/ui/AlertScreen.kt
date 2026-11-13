package ua.alert.wear.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel

@Composable
fun AlertScreen(
    alert: AlertEvent,
    onDismiss: () -> Unit
) {
    // M3 Expressive Danger Tonal Palette (Solid Red or Hazard Yellow — no jarring flash)
    val primaryBg = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFFC81E1E)
        DangerLevel.YELLOW -> Color(0xFFB45309)
        DangerLevel.GREEN -> Color(0xFF047857)
    }
    val secondaryBg = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFF450A0A)
        DangerLevel.YELLOW -> Color(0xFF451A03)
        DangerLevel.GREEN -> Color(0xFF064E3B)
    }
    val accentColor = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFFFFB4AB)
        DangerLevel.YELLOW -> Color(0xFFFDE047)
        DangerLevel.GREEN -> Color(0xFF6EE7B7)
    }

    var elapsedSeconds by remember(alert.timestamp) {
        mutableLongStateOf(maxOf(0L, (System.currentTimeMillis() - alert.timestamp) / 1000L))
    }

    LaunchedEffect(alert.timestamp) {
        while (true) {
            elapsedSeconds = maxOf(0L, (System.currentTimeMillis() - alert.timestamp) / 1000L)
            delay(1000)
        }
    }

    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val formattedTime = String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "wearAlertExpressive")
    val scallopRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scallopRotation"
    )
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(primaryBg, secondaryBg)
                )
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        // M3 Expressive Edge-Hugging Bezel Track & Rotating Scalloped Polygon
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)

            // Outer bezel ring hugging the round watch edge
            drawCircle(
                color = accentColor.copy(alpha = 0.24f),
                radius = (size.minDimension / 2f) - 2.dp.toPx(),
                center = center,
                style = Stroke(width = 2.5.dp.toPx())
            )

            // Rotating M3 Expressive 12-lobe scalloped burst
            rotate(degrees = scallopRotation, pivot = center) {
                val path = createWearScallopedPath(
                    centerX = center.x,
                    centerY = center.y,
                    outerRadius = size.minDimension * 0.42f * waveScale,
                    innerRadius = size.minDimension * 0.35f * waveScale,
                    lobes = 12
                )
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = 0.10f)
                )
            }

            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.minDimension * 0.28f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            // Top Frosted Region Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = alert.region,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Center Stack: Warning Emblem + Bold Threat Title + Expressive Numeral Timer
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(30.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.White.copy(alpha = 0.22f))
                        val w = size.width
                        val h = size.height
                        val triangle = Path().apply {
                            moveTo(w * 0.5f, h * 0.20f)
                            lineTo(w * 0.78f, h * 0.74f)
                            lineTo(w * 0.22f, h * 0.74f)
                            close()
                        }
                        drawPath(
                            path = triangle,
                            color = Color.White,
                            style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawLine(
                            color = Color.White,
                            start = Offset(w * 0.5f, h * 0.41f),
                            end = Offset(w * 0.5f, h * 0.55f),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 1.4.dp.toPx(),
                            center = Offset(w * 0.5f, h * 0.65f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = alert.title.ifEmpty { alert.threatType.ukrainianLabel }.uppercase(),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    lineHeight = 14.sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                // M3 Expressive Numeral Timer Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.26f))
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formattedTime,
                        color = accentColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Edge-Hugging M3 Expressive Bottom Pill Action Button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.White,
                    contentColor = primaryBg
                ),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth(0.74f)
                    .height(38.dp)
            ) {
                Text(
                    text = "СТОП",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

internal fun createWearScallopedPath(
    centerX: Float,
    centerY: Float,
    outerRadius: Float,
    innerRadius: Float,
    lobes: Int
): Path {
    val path = Path()
    val steps = lobes * 8
    val meanRadius = (outerRadius + innerRadius) / 2f
    val amp = (outerRadius - innerRadius) / 2f

    for (i in 0..steps) {
        val theta = (2.0 * PI * i) / steps
        val r = meanRadius + amp * cos(lobes * theta).toFloat()
        val x = centerX + r * cos(theta).toFloat()
        val y = centerY + r * sin(theta).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
