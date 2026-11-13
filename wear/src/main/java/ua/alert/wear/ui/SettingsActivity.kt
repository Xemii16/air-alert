package ua.alert.wear.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.shared.ScheduleConfig
import ua.alert.shared.SelectedRegionConfig
import ua.alert.wear.audio.AlertVibrator
import ua.alert.wear.data.AlertHistoryManager

// M3 Expressive Grouped Card Shapes for Wear OS
private val WearGroupTopShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
private val WearGroupBottomShape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 22.dp, bottomEnd = 22.dp)

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
        val historyManager = AlertHistoryManager.getInstance(this)

        setContent {
            MaterialTheme {
                WatchMainScreen(historyManager = historyManager)
            }
        }
    }
}

@Composable
fun WatchMainScreen(historyManager: AlertHistoryManager) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val history by historyManager.historyFlow.collectAsState()

    val vibrator = remember { AlertVibrator(context) }
    var isTestingVibration by remember { mutableStateOf(false) }

    val prefs = context.getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
    val scheduleJson = prefs.getString("synced_schedule_json", null)
    val scheduleConfig = if (scheduleJson != null) ScheduleConfig.fromJson(scheduleJson) else ScheduleConfig()

    val regionJson = prefs.getString("synced_region_json", null)
    val regionConfig = if (regionJson != null) SelectedRegionConfig.fromJson(regionJson) else SelectedRegionConfig()

    val infiniteTransition = rememberInfiniteTransition(label = "watchHomeEmblem")
    val scallopRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 26000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scallopRotation"
    )

    // M3 Expressive Shape Morphing on the Test Haptics Button
    val testBtnCorner by animateDpAsState(
        targetValue = if (isTestingVibration) 12.dp else 26.dp,
        label = "testBtnCorner"
    )
    val testBtnColor by animateColorAsState(
        targetValue = if (isTestingVibration) Color(0xFFFF5449) else Color(0xFF93000A),
        label = "testBtnColor"
    )

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1015))
            .padding(horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. M3 Expressive Header: Region Pill + Scalloped Safe Emblem
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF1E2433))
                        .border(1.dp, Color(0xFF2E3646), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = regionConfig.activeRegionName,
                        color = Color(0xFFE2E8F0),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(54.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        drawCircle(
                            color = Color(0xFF34D399).copy(alpha = 0.12f),
                            radius = size.minDimension / 2f,
                            center = center
                        )
                        rotate(degrees = scallopRotation, pivot = center) {
                            val path = createWearScallopedPath(
                                centerX = center.x,
                                centerY = center.y,
                                outerRadius = size.minDimension * 0.42f,
                                innerRadius = size.minDimension * 0.33f,
                                lobes = 10
                            )
                            drawPath(
                                path = path,
                                color = Color(0xFF34D399).copy(alpha = 0.24f)
                            )
                        }
                        val w = size.width
                        val h = size.height
                        drawLine(
                            color = Color(0xFF34D399),
                            start = Offset(w * 0.34f, h * 0.52f),
                            end = Offset(w * 0.46f, h * 0.64f),
                            strokeWidth = 2.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = Color(0xFF34D399),
                            start = Offset(w * 0.46f, h * 0.64f),
                            end = Offset(w * 0.68f, h * 0.38f),
                            strokeWidth = 2.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Немає тривоги",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // 2. Shape-Morphing M3 Expressive Haptic Test Button
        item {
            Button(
                onClick = {
                    if (!isTestingVibration) {
                        coroutineScope.launch {
                            isTestingVibration = true
                            vibrator.startRepeatingAlarm()
                            delay(3000)
                            vibrator.stop()
                            isTestingVibration = false
                        }
                    } else {
                        vibrator.stop()
                        isTestingVibration = false
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = testBtnColor,
                    contentColor = Color(0xFFFFDAD6)
                ),
                shape = RoundedCornerShape(testBtnCorner),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (isTestingVibration) "СТОП ТЕСТУ ВІБРАЦІЇ" else "Тест вібрації • 3с",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // 3. M3 Expressive Grouped Info Containers (Schedule + Mode)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WearGroupTopShape)
                        .background(Color(0xFF191D28))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "РОБОЧІ ГОДИНИ",
                            color = Color(0xFFFF897D),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = if (scheduleConfig.isEnabled) {
                                "%02d:%02d – %02d:%02d".format(
                                    scheduleConfig.startHour,
                                    scheduleConfig.startMinute,
                                    scheduleConfig.endHour,
                                    scheduleConfig.endMinute
                                )
                            } else {
                                "Цілодобово (24/7)"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(WearGroupBottomShape)
                        .background(Color(0xFF191D28))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column {
                        Text(
                            text = "РЕЖИМ ТРИВОГИ",
                            color = Color(0xFF34D399),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "Сильна вібрація (без звуку)",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // 4. Alert History Section
        item {
            Text(
                text = "ІСТОРІЯ СПОВІЩЕНЬ",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "Сповіщень ще не було",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(history) { alert ->
                HistoryItem(alert = alert)
            }
        }

        item {
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
fun HistoryItem(alert: AlertEvent) {
    val badgeColor = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFFFF5449)
        DangerLevel.YELLOW -> Color(0xFFFBBF24)
        DangerLevel.GREEN -> Color(0xFF34D399)
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedDate = timeFormatter.format(Date(alert.timestamp))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF191D28))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badgeColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = alert.region,
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF252B3B))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = formattedDate,
                    color = Color(0xFFCBD5E1),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
