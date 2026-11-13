package ua.alert.phone.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import ua.alert.phone.network.BackendClient
import ua.alert.phone.schedule.ScheduleManager
import ua.alert.phone.service.PhoneWearMessageSender

class MainActivity : ComponentActivity() {

    private lateinit var messageSender: PhoneWearMessageSender
    private lateinit var scheduleManager: ScheduleManager
    private lateinit var backendClient: BackendClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        messageSender = PhoneWearMessageSender(this)
        scheduleManager = ScheduleManager.getInstance(this)
        backendClient = BackendClient.getInstance(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                DashboardScreen(
                    messageSender = messageSender,
                    scheduleManager = scheduleManager,
                    backendClient = backendClient
                )
            }
        }
    }
}
