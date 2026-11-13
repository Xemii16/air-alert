package ua.alert.phone.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import ua.alert.shared.ScheduleConfig

@Composable
fun ScheduleDialog(
    initialConfig: ScheduleConfig,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleConfig) -> Unit
) {
    val context = LocalContext.current
    var startHour by remember { mutableIntStateOf(initialConfig.startHour) }
    var startMinute by remember { mutableIntStateOf(initialConfig.startMinute) }
    var endHour by remember { mutableIntStateOf(initialConfig.endHour) }
    var endMinute by remember { mutableIntStateOf(initialConfig.endMinute) }

    fun formatTime(hour: Int, minute: Int): String {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF161922),
        titleContentColor = Color.White,
        title = {
            Text(
                text = "Робочі години годинника",
                fontWeight = FontWeight.Black,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Годинник вмикатиме повноекранну тривогу та сильну вібрацію лише у вибраний проміжок часу (відбій проходить завжди).",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ExpressiveTimeCard(
                        label = "ПОЧАТОК",
                        timeText = formatTime(startHour, startMinute),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    startHour = h
                                    startMinute = m
                                },
                                startHour,
                                startMinute,
                                true
                            ).show()
                        }
                    )

                    ExpressiveTimeCard(
                        label = "КІНЕЦЬ",
                        timeText = formatTime(endHour, endMinute),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    endHour = h
                                    endMinute = m
                                },
                                endHour,
                                endMinute,
                                true
                            ).show()
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        initialConfig.copy(
                            isEnabled = true,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute
                        )
                    )
                },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF5449),
                    contentColor = Color(0xFF3B0002)
                )
            ) {
                Text("Зберегти", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати", color = Color(0xFF94A3B8))
            }
        }
    )
}

@Composable
private fun ExpressiveTimeCard(
    label: String,
    timeText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF222838),
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color(0xFFFF897D),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = timeText,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
