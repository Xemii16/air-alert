package ua.alert.phone.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import ua.alert.shared.ProtocolConstants

class PhoneWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        when (messageEvent.path) {
            ProtocolConstants.PATH_DISMISS_ALERT -> {
                Log.d(TAG, "Received dismiss acknowledgement from watch.")
            }
            else -> {
                Log.d(TAG, "Received message from watch: ${messageEvent.path}")
            }
        }
    }

    companion object {
        private const val TAG = "PhoneWearListener"
    }
}
