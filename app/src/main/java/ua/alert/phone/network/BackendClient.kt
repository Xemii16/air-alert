package ua.alert.phone.network

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ua.alert.shared.RegionNode
import ua.alert.shared.SelectedRegionConfig
import ua.alert.shared.ThreatType

class BackendClient(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _selectedRegion = MutableStateFlow(loadSelectedRegion())
    val selectedRegion: StateFlow<SelectedRegionConfig> = _selectedRegion.asStateFlow()

    private val _backendUrl = MutableStateFlow(loadBackendUrl())
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _regionsTree = MutableStateFlow(RegionNode.defaultUkraineRegions())
    val regionsTree: StateFlow<List<RegionNode>> = _regionsTree.asStateFlow()

    private fun loadSelectedRegion(): SelectedRegionConfig {
        val json = prefs.getString(KEY_SELECTED_REGION, null)
        return if (json != null) SelectedRegionConfig.fromJson(json) else SelectedRegionConfig()
    }

    private fun loadBackendUrl(): String {
        return prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
    }

    fun saveBackendUrl(url: String) {
        val cleaned = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_BACKEND_URL, cleaned).apply()
        _backendUrl.value = cleaned
    }

    suspend fun updateSelectedRegion(newRegion: SelectedRegionConfig): Boolean {
        val previous = _selectedRegion.value
        prefs.edit().putString(KEY_SELECTED_REGION, newRegion.toJson()).apply()
        _selectedRegion.value = newRegion

        // Update FCM topic subscriptions
        updateFcmTopics(previous, newRegion)

        // Register with Spring Boot backend
        return registerDeviceWithBackend(newRegion)
    }

    private suspend fun updateFcmTopics(oldRegion: SelectedRegionConfig, newRegion: SelectedRegionConfig) {
        withContext(Dispatchers.IO) {
            try {
                val fcm = FirebaseMessaging.getInstance()
                for (oldId in oldRegion.allSubscribedIds()) {
                    if (oldId !in newRegion.allSubscribedIds()) {
                        fcm.unsubscribeFromTopic("region_$oldId").await()
                    }
                }
                for (newId in newRegion.allSubscribedIds()) {
                    fcm.subscribeToTopic("region_$newId").await()
                }
                Log.i(TAG, "Subscribed to FCM topics for region IDs: ${newRegion.allSubscribedIds()}")
            } catch (e: Exception) {
                Log.w(TAG, "FCM topic subscription note (will retry when Firebase is configured): ${e.message}")
            }
        }
    }

    suspend fun fetchRegionsFromBackend(): List<RegionNode> = withContext(Dispatchers.IO) {
        try {
            val url = URL("${_backendUrl.value}/api/v1/regions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val parsed = RegionNode.parseStatesList(response)
                _regionsTree.value = parsed
                return@withContext parsed
            }
        } catch (e: Exception) {
            Log.d(TAG, "Using built-in Ukraine Alarm regions tree (backend unreachable: ${e.message})")
        }
        _regionsTree.value
    }

    suspend fun registerDeviceWithBackend(region: SelectedRegionConfig = _selectedRegion.value): Boolean =
        withContext(Dispatchers.IO) {
            val token = try {
                FirebaseMessaging.getInstance().token.await()
            } catch (e: Exception) {
                "local-device-token"
            }
            registerTokenWithBackend(token, region)
        }

    suspend fun registerTokenWithBackend(
        fcmToken: String,
        region: SelectedRegionConfig = _selectedRegion.value
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?: "android-phone-default"

            val payload = JSONObject().apply {
                put("deviceId", deviceId)
                put("fcmToken", fcmToken)
                put("stateId", region.stateId)
                if (region.districtId != null) put("districtId", region.districtId)
                if (region.communityId != null) put("communityId", region.communityId)
                put("regionName", region.activeRegionName)
            }

            val url = URL("${_backendUrl.value}/api/v1/devices/register")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Backend device registration skipped (${e.message})")
            false
        }
    }

    companion object {
        private const val TAG = "BackendClient"
        private const val PREFS_NAME = "air_alert_backend_prefs"
        private const val KEY_SELECTED_REGION = "selected_region_json"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val DEFAULT_BACKEND_URL = "http://10.0.2.2:8080"

        @Volatile
        private var instance: BackendClient? = null

        fun getInstance(context: Context): BackendClient {
            return instance ?: synchronized(this) {
                instance ?: BackendClient(context.applicationContext).also { instance = it }
            }
        }
    }
}
