package ua.alert.phone.service

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ua.alert.phone.network.BackendClient
import ua.alert.shared.ActiveAlertLevel
import ua.alert.shared.AlertEvent
import ua.alert.shared.AlertTransitionPolicy
import ua.alert.shared.ThreatType
import ua.alert.shared.WatchAction

class AirAlertFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "Refreshed FCM token; re-syncing FCM topic subscriptions")
        runBlocking {
            withTimeoutOrNull(15_000L) {
                val client = BackendClient.getInstance(applicationContext)
                client.ensureTopicSubscriptions(forceResubscribe = true)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val rawAlarmType = data["alarmType"]?.takeIf { it.isNotBlank() }
            ?: data["alertType"]
            ?: "AIR"
        if (AlertTransitionPolicy.isIgnoredAlarmType(rawAlarmType)) {
            Log.d(TAG, "Ignoring INFO FCM message")
            return
        }

        val regionId = data["regionId"] ?: "31"
        val regionName = data["regionName"] ?: BackendClient.getInstance(this).selectedRegion.value.activeRegionName
        val hasSnapshotField = data.containsKey("activeAlertLevels")
        val parsedLevels = ActiveAlertLevel.fromJsonArray(data["activeAlertLevels"], rawAlarmType)

        // Core WebHookMessageModel rule: activeAlertLevels.length > 0 determines active state
        val isActive = if (hasSnapshotField) {
            parsedLevels.isNotEmpty()
        } else {
            data["isActive"]?.toBooleanStrictOrNull() ?: (rawAlarmType != "ALL_CLEAR")
        }

        val threatType = if (!isActive) ThreatType.ALL_CLEAR else ThreatType.fromApiType(rawAlarmType)
        if (threatType == ThreatType.INFO) {
            return
        }

        val dangerLevel = data["dangerLevel"]?.takeIf { it.isNotBlank() }?.let {
            if (!isActive) ua.alert.shared.DangerLevel.GREEN else ActiveAlertLevel.parseDangerLevel(it)
        } ?: AlertEvent.computeOverallDangerLevel(isActive, parsedLevels, threatType)

        val eventStatus = data["eventStatus"] ?: ""
        val eventReason = data["eventReason"] ?: ""
        val timestamp = data["timestamp"]?.toLongOrNull() ?: System.currentTimeMillis()

        val alertEvent = AlertEvent(
            regionId = regionId,
            threatType = threatType,
            dangerLevel = dangerLevel,
            region = regionName,
            title = threatType.ukrainianLabel,
            text = threatType.instructionText,
            timestamp = timestamp,
            isActive = isActive,
            alarmType = threatType.apiCode,
            activeAlertLevels = if (isActive) parsedLevels else emptyList(),
            eventStatus = eventStatus,
            eventReason = eventReason
        )

        val previous = currentAlertState.value
        val watchAction = AlertTransitionPolicy.evaluate(previous, alertEvent)

        Log.i(
            TAG,
            "FCM snapshot received: region=$regionName ($regionId), active=$isActive, " +
                "danger=$dangerLevel, levels=${alertEvent.activeAlertLevels.size}, " +
                "eventReason='$eventReason', watchAction=$watchAction"
        )
        updateCurrentAlertState(alertEvent)

        if (watchAction == WatchAction.NONE) {
            return
        }

        // Block on FCM's background worker thread until Wear OS delivery completes,
        // preventing FirebaseMessagingService.onDestroy() from cancelling the send mid-flight.
        runBlocking {
            val sent = withTimeoutOrNull(10_000L) {
                val wearSender = PhoneWearMessageSender(applicationContext)
                when (watchAction) {
                    WatchAction.TRIGGER_ALARM, WatchAction.TRIGGER_CLEAR -> wearSender.sendAlertTrigger(alertEvent)
                    WatchAction.SYNC_PASSIVE -> wearSender.syncAlertStatus(alertEvent)
                    WatchAction.NONE -> true
                }
            } ?: false
            if (!sent) {
                Log.w(TAG, "Timed out or failed sending $watchAction to Wear OS watch")
            }
        }
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
