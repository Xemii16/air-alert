package ua.alert.phone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ua.alert.phone.network.BackendClient
import ua.alert.phone.schedule.ScheduleManager
import ua.alert.phone.service.PhoneWearMessageSender

private val M3ExpressiveDarkScheme = darkColorScheme(
    primary = Color(0xFFFF5449),
    onPrimary = Color(0xFF3B0002),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF003824),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF422006),
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = Color(0xFF0E1015),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF161922),
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF232734),
    onSurfaceVariant = Color(0xFF94A3B8)
)

class MainActivity : ComponentActivity() {

    private lateinit var messageSender: PhoneWearMessageSender
    private lateinit var scheduleManager: ScheduleManager
    private lateinit var backendClient: BackendClient

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        messageSender = PhoneWearMessageSender(this)
        scheduleManager = ScheduleManager.getInstance(this)
        backendClient = BackendClient.getInstance(this)

        setContent {
            MaterialTheme(
                colorScheme = M3ExpressiveDarkScheme
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
