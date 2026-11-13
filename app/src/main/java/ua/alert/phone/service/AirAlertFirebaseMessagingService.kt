package ua.alert.phone.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ua.alert.phone.network.BackendClient
import ua.alert.phone.schedule.ScheduleManager
import ua.alert.shared.AlertEvent
import ua.alert.shared.ThreatType

class AirAlertFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Refreshed FCM token; syncing with Spring Boot backend")
        serviceScope.launch {
            BackendClient.getInstance(applicationContext).registerTokenWithBackend(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val regionId = data["regionId"] ?: "31"
        val regionName = data["regionName"] ?: BackendClient.getInstance(this).selectedRegion.value.activeRegionName
        val rawAlertType = data["alertType"] ?: "AIR"
        val isActive = data["isActive"]?.toBooleanStrictOrNull() ?: (rawAlertType != "ALL_CLEAR")
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        val threatType = if (!isActive) ThreatType.ALL_CLEAR else ThreatType.fromApiType(rawAlertType)

        val alertEvent = AlertEvent(
            regionId = regionId,
            threatType = threatType,
            dangerLevel = threatType.defaultDangerLevel,
            region = regionName,
            title = threatType.ukrainianLabel,
            text = threatType.instructionText,
            timestamp = timestamp,
            isActive = isActive
        )

        Log.i(TAG, "FCM alert received from backend: region=$regionName ($regionId), type=$threatType, active=$isActive")
        updateCurrentAlertState(alertEvent)

        val scheduleManager = ScheduleManager.getInstance(this)
        val isPermitted = if (!isActive || threatType == ThreatType.ALL_CLEAR) {
            // Always allow All Clear to silence any ongoing alarm on the watch
            true
        } else {
            scheduleManager.isAlertPermittedNow()
        }

        if (!isPermitted) {
            Log.i(TAG, "Watch alert skipped because current time is outside configured working hours")
            return
        }

        serviceScope.launch {
            PhoneWearMessageSender(applicationContext).sendAlertTrigger(alertEvent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "AirAlertFcmService"

        private val _currentAlertState = MutableStateFlow<AlertEvent?>(null)
        val currentAlertState: StateFlow<AlertEvent?> = _currentAlertState.asStateFlow()

        fun updateCurrentAlertState(event: AlertEvent?) {
            _currentAlertState.value = event
        }
    }
}
