package ua.alert.wear.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import java.util.Locale
import kotlinx.coroutines.delay
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel

@Composable
fun AlertScreen(
    alert: AlertEvent,
    onDismiss: () -> Unit
) {
    // Official com.ukrainealarm crimson red or hazard yellow background
    val primaryBg = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFFC81E1E)
        DangerLevel.YELLOW -> Color(0xFFCA8A04)
        DangerLevel.GREEN -> Color(0xFF15803D)
    }
    val secondaryBg = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFF7F1D1D)
        DangerLevel.YELLOW -> Color(0xFF713F12)
        DangerLevel.GREEN -> Color(0xFF14532D)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(primaryBg, secondaryBg)
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background concentric circles matching official com.ukrainealarm aesthetic
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                color = Color.White.copy(alpha = 0.07f),
                radius = size.minDimension * 0.46f,
                center = center
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.12f),
                radius = size.minDimension * 0.34f,
                center = center
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            // Selected Region Name (top header like com.ukrainealarm)
            Text(
                text = alert.region,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(3.dp))

            // Iconic UkraineAlarm Megaphone / Warning Emblem
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(color = Color.White.copy(alpha = 0.22f))
                    // Draw stylized warning triangle / siren icon
                    val w = size.width
                    val h = size.height
                    val triangle = Path().apply {
                        moveTo(w * 0.5f, h * 0.22f)
                        lineTo(w * 0.78f, h * 0.74f)
                        lineTo(w * 0.22f, h * 0.74f)
                        close()
                    }
                    drawPath(
                        path = triangle,
                        color = Color.White,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(w * 0.5f, h * 0.42f),
                        end = Offset(w * 0.5f, h * 0.56f),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.5.dp.toPx(),
                        center = Offset(w * 0.5f, h * 0.65f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            // Bold Threat Title ("ПОВІТРЯНА ТРИВОГА!")
            Text(
                text = alert.title.ifEmpty { alert.threatType.ukrainianLabel }.uppercase(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 2,
                lineHeight = 15.sp
            )

            // Live Elapsed Timer
            Text(
                text = formattedTime,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Official com.ukrainealarm style white pill stop button
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.White,
                    contentColor = Color(0xFFB91C1C)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .width(96.dp)
                    .height(36.dp)
            ) {
                Text(
                    text = "СТОП",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
