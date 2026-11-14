package ua.alert.wear.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import ua.alert.shared.AlertEvent
import ua.alert.shared.DangerLevel
import ua.alert.shared.ProtocolConstants
import ua.alert.shared.ThreatType
import ua.alert.wear.audio.AlertVibrator
import ua.alert.wear.data.AlertHistoryManager
import ua.alert.wear.sensor.WearOffBodyDetector
import ua.alert.wear.ui.FullScreenAlertActivity

class AlertAlarmService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var vibrator: AlertVibrator
    private lateinit var historyManager: AlertHistoryManager
    private lateinit var offBodyDetector: WearOffBodyDetector

    private var wakeLock: PowerManager.WakeLock? = null
    private var autoDismissJob: kotlinx.coroutines.Job? = null
    private var offBodyObserverHandle: AutoCloseable? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        vibrator = AlertVibrator(this)
        historyManager = AlertHistoryManager.getInstance(this)
        offBodyDetector = WearOffBodyDetector(this)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_ALERT -> {
                val eventJson = intent.getStringExtra(EXTRA_ALERT_JSON)
                if (eventJson != null) {
                    val event = AlertEvent.fromJson(eventJson)
                    handleStartAlert(event)
                }
            }
            ACTION_ALL_CLEAR -> {
                val eventJson = intent.getStringExtra(EXTRA_ALERT_JSON)
                val event = if (eventJson != null) {
                    AlertEvent.fromJson(eventJson)
                } else {
                    AlertEvent(
                        threatType = ThreatType.ALL_CLEAR,
                        dangerLevel = DangerLevel.GREEN,
                        region = _activeAlert.value?.region ?: "Україна",
                        title = "Відбій тривоги",
                        isActive = false
                    )
                }
                handleAllClear(event)
            }
            ACTION_DISMISS_FROM_USER -> {
                handleUserDismiss()
            }
            ACTION_STOP_FROM_PHONE -> {
                handleStopFromPhone()
            }
        }

        return START_NOT_STICKY
    }

    private fun handleStartAlert(event: AlertEvent) {
        Log.i(TAG, "Starting active alert (hard vibration only): ${event.title} [Danger: ${event.dangerLevel}]")
        _activeAlert.value = event
        _isRinging.value = true

        historyManager.addAlert(event)
        acquireWakeLock()

        // 1. Start Hard Repeating Vibration (no sound)
        vibrator.startRepeatingAlarm()

        // 2. Start Foreground Service with Full-Screen Intent Notification
        val notification = buildAlarmNotification(event)
        startForegroundWithSpecialUse(notification)
        startOffBodyObservation()

        // 3. Launch Full-Screen Activity directly (best effort; fullScreenIntent handles fallback)
        try {
            val activityIntent = Intent(this, FullScreenAlertActivity::class.java).apply {
                putExtra(EXTRA_ALERT_JSON, event.toJson())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct activity launch blocked (relying on fullScreenIntent): ${e.message}")
        }
    }

    private fun handleAllClear(event: AlertEvent) {
        Log.i(TAG, "Handling All Clear: ${event.title}")
        _activeAlert.value = event
        _isRinging.value = false

        historyManager.addAlert(event)
        acquireWakeLock()

        // Stop continuous alarm vibration and play All Clear confirmation pulse
        vibrator.stop()
        vibrator.playAllClearPulse()

        // Update / start foreground notification to All Clear
        val notification = buildAllClearNotification(event)
        startForegroundWithSpecialUse(notification)
        startOffBodyObservation()

        // Launch / update Full-Screen Activity to All Clear state
        try {
            val activityIntent = Intent(this, FullScreenAlertActivity::class.java).apply {
                putExtra(EXTRA_ALERT_JSON, event.toJson())
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(activityIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Direct all-clear activity launch blocked (relying on fullScreenIntent): ${e.message}")
        }

        // Cancel any previous auto-clear timer and start a fresh one
        autoDismissJob?.cancel()
        autoDismissJob = serviceScope.launch {
            kotlinx.coroutines.delay(15000)
            dismissForegroundAndNotification()
            stopSelf()
        }
    }

    private fun dismissForegroundAndNotification() {
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
    }

    private fun startForegroundWithSpecialUse(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun handleUserDismiss() {
        Log.i(TAG, "User discarded alert notification from watch UI")
        autoDismissJob?.cancel()
        _isRinging.value = false
        vibrator.stop()
        dismissForegroundAndNotification()

        // Send dismissal sync back to phone, then stop service cleanly
        serviceScope.launch {
            try {
                withTimeoutOrNull(2_000L) {
                    val nodeClient = Wearable.getNodeClient(applicationContext)
                    val messageClient = Wearable.getMessageClient(applicationContext)
                    val nodes = nodeClient.connectedNodes.await()
                    for (node in nodes) {
                        messageClient.sendMessage(node.id, ProtocolConstants.PATH_DISMISS_ALERT, ByteArray(0)).await()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send dismiss sync to phone", e)
            } finally {
                stopSelf()
            }
        }
    }

    private fun handleStopFromPhone() {
        Log.i(TAG, "Stop command received from phone companion")
        autoDismissJob?.cancel()
        _isRinging.value = false
        _activeAlert.value = null
        vibrator.stop()
        dismissForegroundAndNotification()
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AirAlertWear:ScreenWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(10 * 60 * 1000L) // 10 minutes safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing wake lock", e)
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Air Alert Emergency Alarms",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority full-screen vibration alarms for air raid alerts"
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildAlarmNotification(event: AlertEvent): Notification {
        val fullScreenIntent = Intent(this, FullScreenAlertActivity::class.java).apply {
            putExtra(EXTRA_ALERT_JSON, event.toJson())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlertAlarmService::class.java).apply {
            action = ACTION_DISMISS_FROM_USER
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val reasons = event.displayReasons()
        val contentSummary = if (reasons.isNotEmpty()) {
            "${event.region} • ${reasons.joinToString(", ")}"
        } else {
            event.region
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(event.title)
            .setContentText(contentSummary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentSummary))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .setDeleteIntent(dismissPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Вимкнути", dismissPendingIntent)
            .build()
    }

    private fun buildAllClearNotification(event: AlertEvent): Notification {
        val fullScreenIntent = Intent(this, FullScreenAlertActivity::class.java).apply {
            putExtra(EXTRA_ALERT_JSON, event.toJson())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            2,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, AlertAlarmService::class.java).apply {
            action = ACTION_DISMISS_FROM_USER
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            3,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Відбій тривоги")
            .setContentText(event.region)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setDeleteIntent(dismissPendingIntent)
            .build()
    }

    private fun startOffBodyObservation() {
        stopOffBodyObservation()
        offBodyObserverHandle = offBodyDetector.observeWhileActive { isOnWrist ->
            if (!isOnWrist) {
                Log.i(TAG, "Watch removed from wrist during active alert; stopping vibration & dismissing notification")
                autoDismissJob?.cancel()
                _isRinging.value = false
                vibrator.stop()
                dismissForegroundAndNotification()
                _closeFullScreenSignal.tryEmit(Unit)
                stopOffBodyObservation()
                stopSelf()
            }
        }
    }

    private fun stopOffBodyObservation() {
        try {
            offBodyObserverHandle?.close()
        } catch (_: Exception) {
        }
        offBodyObserverHandle = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        _isRinging.value = false
        vibrator.stop()
        stopOffBodyObservation()
        dismissForegroundAndNotification()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        vibrator.stop()
        stopOffBodyObservation()
        releaseWakeLock()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AlertAlarmService"
        const val CHANNEL_ID = "air_alert_alarm_channel_v2"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_ALERT = "ua.alert.wear.START_ALERT"
        const val ACTION_ALL_CLEAR = "ua.alert.wear.ALL_CLEAR"
        const val ACTION_DISMISS_FROM_USER = "ua.alert.wear.DISMISS_USER"
        const val ACTION_STOP_FROM_PHONE = "ua.alert.wear.STOP_PHONE"

        const val EXTRA_ALERT_JSON = "extra_alert_json"

        @Volatile
        var instance: AlertAlarmService? = null
            private set

        private val _activeAlert = MutableStateFlow<AlertEvent?>(null)
        val activeAlert: StateFlow<AlertEvent?> = _activeAlert.asStateFlow()

        private val _isRinging = MutableStateFlow(false)
        val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

        private val _closeFullScreenSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val closeFullScreenSignal: SharedFlow<Unit> = _closeFullScreenSignal.asSharedFlow()

        fun updatePassiveStatus(event: AlertEvent) {
            if (event.threatType == ThreatType.INFO) return
            _activeAlert.value = event
        }

        fun stopRingingFromWatch(context: Context) {
            val appContext = context.applicationContext
            _isRinging.value = false
            // Cancel system HAL vibration synchronously even if the service is not currently running or unbound.
            AlertVibrator(appContext).stop()
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            if (instance != null) {
                val intent = Intent(appContext, AlertAlarmService::class.java).apply {
                    action = ACTION_DISMISS_FROM_USER
                }
                appContext.startService(intent)
            }
        }

        fun dismissNotificationFromWatch(context: Context) {
            stopRingingFromWatch(context)
        }
    }
}
