package ua.alert.phone.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import ua.alert.shared.AlertEvent
import ua.alert.shared.ProtocolConstants
import ua.alert.shared.ScheduleConfig
import ua.alert.shared.SelectedRegionConfig

class PhoneWearMessageSender(private val context: Context) {

    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    suspend fun getConnectedNodes(): List<Node> = withContext(Dispatchers.IO) {
        try {
            nodeClient.connectedNodes.await()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get connected nodes", e)
            emptyList()
        }
    }

    suspend fun sendAlertTrigger(event: AlertEvent): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedNodes()
        if (nodes.isEmpty()) {
            Log.w(TAG, "No connected Wear OS nodes found to send alert trigger")
            return@withContext false
        }

        val payload = event.toJson().toByteArray(Charsets.UTF_8)
        var anySuccess = false

        for (node in nodes) {
            try {
                messageClient.sendMessage(node.id, ProtocolConstants.PATH_TRIGGER_ALERT, payload).await()
                Log.d(TAG, "Sent alert trigger to node: ${node.displayName} (${node.id})")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send alert trigger to node: ${node.id}", e)
            }
        }
        anySuccess
    }

    suspend fun sendStopAlert(): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedNodes()
        if (nodes.isEmpty()) {
            Log.w(TAG, "No connected Wear OS nodes found to stop alert")
            return@withContext false
        }

        val payload = ByteArray(0)
        var anySuccess = false

        for (node in nodes) {
            try {
                messageClient.sendMessage(node.id, ProtocolConstants.PATH_STOP_ALERT, payload).await()
                Log.d(TAG, "Sent stop alert to node: ${node.displayName}")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send stop alert to node: ${node.id}", e)
            }
        }
        anySuccess
    }

    suspend fun syncSchedule(schedule: ScheduleConfig): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedNodes()
        if (nodes.isEmpty()) return@withContext false

        val payload = schedule.toJson().toByteArray(Charsets.UTF_8)
        var anySuccess = false

        for (node in nodes) {
            try {
                messageClient.sendMessage(node.id, ProtocolConstants.PATH_SYNC_SCHEDULE, payload).await()
                Log.d(TAG, "Synced schedule to node: ${node.displayName}")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync schedule to node: ${node.id}", e)
            }
        }
        anySuccess
    }

    suspend fun syncSelectedRegion(region: SelectedRegionConfig): Boolean = withContext(Dispatchers.IO) {
        val nodes = getConnectedNodes()
        if (nodes.isEmpty()) return@withContext false

        val payload = region.toJson().toByteArray(Charsets.UTF_8)
        var anySuccess = false

        for (node in nodes) {
            try {
                messageClient.sendMessage(node.id, ProtocolConstants.PATH_SYNC_REGION, payload).await()
                Log.d(TAG, "Synced selected region (${region.activeRegionName}) to node: ${node.displayName}")
                anySuccess = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync region to node: ${node.id}", e)
            }
        }
        anySuccess
    }

    companion object {
        private const val TAG = "PhoneWearMessageSender"
    }
}
