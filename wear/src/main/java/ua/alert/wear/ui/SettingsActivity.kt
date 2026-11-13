package ua.alert.wear.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
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

    val prefs = context.getSharedPreferences("wear_schedule_prefs", Context.MODE_PRIVATE)
    val scheduleJson = prefs.getString("synced_schedule_json", null)
    val scheduleConfig = if (scheduleJson != null) ScheduleConfig.fromJson(scheduleJson) else ScheduleConfig()

    val regionJson = prefs.getString("synced_region_json", null)
    val regionConfig = if (regionJson != null) SelectedRegionConfig.fromJson(regionJson) else SelectedRegionConfig()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF18191B))
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Region Header + com.ukrainealarm concentric status emblem
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
            ) {
                Text(
                    text = regionConfig.activeRegionName,
                    color = Color(0xFFA1A1AA),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color(0xFF3F3F46).copy(alpha = 0.25f), radius = size.minDimension / 2f)
                        drawCircle(color = Color(0xFF3F3F46).copy(alpha = 0.45f), radius = size.minDimension / 2.8f)
                        drawCircle(color = Color(0xFF22C55E), radius = 6.dp.toPx())
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Немає тривоги",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // Test Hard Vibration Button
        item {
            Button(
                onClick = {
                    coroutineScope.launch {
                        vibrator.startRepeatingAlarm()
                        delay(3000)
                        vibrator.stop()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFFC81E1E),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text("Тест сильної вібрації (3с)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Active Hours Info Card
        item {
            Card(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    Text(
                        text = "Робочі години:",
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp
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
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // History Section Header
        item {
            Text(
                text = "Історія сповіщень",
                color = Color(0xFF9CA3AF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
            )
        }

        if (history.isEmpty()) {
            item {
                Text(
                    text = "Сповіщень ще не було",
                    color = Color.Gray,
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
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun HistoryItem(alert: AlertEvent) {
    val badgeColor = when (alert.dangerLevel) {
        DangerLevel.RED -> Color(0xFFDC2626)
        DangerLevel.YELLOW -> Color(0xFFEAB308)
        DangerLevel.GREEN -> Color(0xFF16A34A)
    }

    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val formattedDate = timeFormatter.format(Date(alert.timestamp))

    Card(
        onClick = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(badgeColor, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = alert.region,
                    color = Color(0xFF9CA3AF),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            Text(
                text = formattedDate,
                color = Color(0xFF71717A),
                fontSize = 10.sp
            )
        }
    }
}
