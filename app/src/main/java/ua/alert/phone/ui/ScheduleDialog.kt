package ua.alert.phone.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ua.alert.shared.ScheduleConfig
import java.util.Locale

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
        title = { Text(text = "Налаштування робочих годин") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Оберіть проміжок часу доби, протягом якого сповіщення будуть вмикати тривогу на годиннику. Підтримується також нічний режим (наприклад, 22:00 – 07:00).",
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Початок дії:", fontSize = 15.sp)
                    OutlinedButton(onClick = {
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
                    }) {
                        Text(formatTime(startHour, startMinute))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Кінець дії:", fontSize = 15.sp)
                    OutlinedButton(onClick = {
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
                    }) {
                        Text(formatTime(endHour, endMinute))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(
                    initialConfig.copy(
                        isEnabled = true,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute
                    )
                )
            }) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}
