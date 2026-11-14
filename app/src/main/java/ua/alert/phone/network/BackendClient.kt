package ua.alert.phone.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ua.alert.phone.service.AirAlertFirebaseMessagingService
import ua.alert.phone.service.PhoneWearMessageSender
import ua.alert.shared.ActiveAlertLevel
import ua.alert.shared.AlertEvent
import ua.alert.shared.AlertTransitionPolicy
import ua.alert.shared.DangerLevel
import ua.alert.shared.RegionNode
import ua.alert.shared.SelectedRegionConfig
import ua.alert.shared.ThreatType
import ua.alert.shared.WatchAction

data class RegionsSourceState(
    val isLoading: Boolean = false,
    val isLiveFromApi: Boolean = false,
    val stateCount: Int = 0,
    val errorMessage: String? = null
)

data class ServerPingInfo(
    val normalizedUrl: String,
    val serviceName: String,
    val apiVersion: Int,
    val ukraineAlarmConfigured: Boolean,
    val regionsCount: Int,
    val latencyMs: Long
)

class BackendClient private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val topicSyncMutex = Mutex()
    @Volatile
    private var topicRetryJob: Job? = null

    private val _isServerConfigured = MutableStateFlow(loadIsServerConfigured())
    val isServerConfigured: StateFlow<Boolean> = _isServerConfigured.asStateFlow()

    private val _selectedRegion = MutableStateFlow(loadSelectedRegion())
    val selectedRegion: StateFlow<SelectedRegionConfig> = _selectedRegion.asStateFlow()

    private val _backendUrl = MutableStateFlow(loadBackendUrl())
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _apiToken = MutableStateFlow(loadApiToken())
    val apiToken: StateFlow<String> = _apiToken.asStateFlow()

    private val _regionsTree = MutableStateFlow(loadInitialRegions())
    val regionsTree: StateFlow<List<RegionNode>> = _regionsTree.asStateFlow()

    private val _isTopicSyncPending = MutableStateFlow(prefs.getBoolean(KEY_TOPIC_SYNC_PENDING, false))
    val isTopicSyncPending: StateFlow<Boolean> = _isTopicSyncPending.asStateFlow()

    private val _regionsSourceState = MutableStateFlow(
        RegionsSourceState(
            isLoading = false,
            isLiveFromApi = prefs.contains(KEY_CACHED_REGIONS_JSON),
            stateCount = _regionsTree.value.size
        )
    )
    val regionsSourceState: StateFlow<RegionsSourceState> = _regionsSourceState.asStateFlow()

    private fun loadIsServerConfigured(): Boolean {
        return prefs.getBoolean(KEY_SERVER_CONFIGURED, false)
    }

    private fun loadSelectedRegion(): SelectedRegionConfig {
        val raw = prefs.getString(KEY_SELECTED_REGION, null) ?: return SelectedRegionConfig()
        return SelectedRegionConfig.fromJson(raw)
    }

    private fun loadBackendUrl(): String {
        return prefs.getString(KEY_BACKEND_URL, "") ?: ""
    }

    private fun loadApiToken(): String {
        return prefs.getString(KEY_UKRAINE_ALARM_TOKEN, "") ?: ""
    }

    private fun loadInitialRegions(): List<RegionNode> {
        val cachedJson = prefs.getString(KEY_CACHED_REGIONS_JSON, null)
        if (!cachedJson.isNullOrBlank()) {
            val parsed = RegionNode.parseStatesList(cachedJson)
            if (parsed.isNotEmpty()) return parsed
        }
        return RegionNode.defaultUkraineRegions()
    }

    private fun isDirectUkraineAlarmApi(url: String = _backendUrl.value): Boolean {
        return url.trim().contains("api.ukrainealarm.com", ignoreCase = true)
    }

    fun normalizeServerUrl(rawUrl: String): String = normalizeUrl(rawUrl)

    /**
     * Pings the candidate server and cryptographically/structurally verifies
     * it is an authentic Air-Alert backend (/api/v1/ping with challenge echo
     * and service="air-alert-backend") or authorized UkraineAlarm API v3 server.
     */
    suspend fun verifyServer(rawUrl: String, token: String = ""): Result<ServerPingInfo> =
        withContext(Dispatchers.IO) {
            val normalizedUrl = normalizeServerUrl(rawUrl)
            val cleanedToken = token.trim()

            if (normalizedUrl.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Введіть URL сервера тривог"))
            }

            try {
                URL(normalizedUrl)
            } catch (_: Exception) {
                return@withContext Result.failure(IllegalArgumentException("Некоректний формат URL"))
            }

            val directApi = isDirectUkraineAlarmApi(normalizedUrl)
            if (directApi && cleanedToken.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Для api.ukrainealarm.com потрібен API-токен")
                )
            }

            val startNs = System.nanoTime()
            try {
                if (directApi) {
                    val (code, body) = executeGet(
                        endpoint = "$normalizedUrl/api/v3/regions",
                        authHeader = cleanedToken
                    )
                    val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
                    if (code !in 200..299 || body.isNullOrBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("UkraineAlarm API повернуло HTTP $code. Перевірте токен.")
                        )
                    }
                    val states = RegionNode.parseStatesList(body)
                    if (states.isEmpty()) {
                        return@withContext Result.failure(
                            IllegalStateException("Отримана відповідь не містить списку регіонів UkraineAlarm.")
                        )
                    }
                    return@withContext Result.success(
                        ServerPingInfo(
                            normalizedUrl = normalizedUrl,
                            serviceName = "ukrainealarm-api-v3",
                            apiVersion = 3,
                            ukraineAlarmConfigured = true,
                            regionsCount = states.size,
                            latencyMs = latencyMs
                        )
                    )
                }

                val challenge = UUID.randomUUID().toString()
                val encodedChallenge = URLEncoder.encode(challenge, "UTF-8")
                val (code, body) = executeGet(
                    endpoint = "$normalizedUrl/api/v1/ping?challenge=$encodedChallenge",
                    authHeader = null
                )
                val latencyMs = (System.nanoTime() - startNs) / 1_000_000L

                if (code !in 200..299 || body.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IllegalStateException("Сервер повернув HTTP $code замість відповіді Air Alert (/api/v1/ping)")
                    )
                }

                validatePingJson(
                    body = body,
                    expectedChallenge = challenge,
                    normalizedUrl = normalizedUrl,
                    latencyMs = latencyMs
                )
            } catch (e: Exception) {
                Log.w(TAG, "Server ping failed for $normalizedUrl: ${e.message}")
                Result.failure(
                    IllegalStateException("Сервер недоступний. Перевірте адресу або оберіть інший сервер.")
                )
            }
        }

    /**
     * Verifies the server via /api/v1/ping, saves it as configured, and syncs
     * regions, device registration, Watch region, and initial alert status.
     */
    suspend fun verifyAndSaveServer(rawUrl: String, token: String = ""): Result<ServerPingInfo> {
        val pingResult = verifyServer(rawUrl, token)
        if (pingResult.isFailure) return pingResult

        val info = pingResult.getOrThrow()
        saveBackendSettings(info.normalizedUrl, token.trim(), markConfigured = true)
        fetchRegionsFromBackend(forceRefresh = true)
        ensureTopicSubscriptions(_selectedRegion.value, forceResubscribe = true)
        val wearSender = PhoneWearMessageSender(appContext)
        wearSender.syncSetupState(true)
        wearSender.syncSelectedRegion(_selectedRegion.value)
        fetchCurrentAlertStatus(_selectedRegion.value)
        return Result.success(info)
    }

    fun saveBackendSettings(url: String, token: String = _apiToken.value, markConfigured: Boolean = true) {
        val cleanedUrl = normalizeServerUrl(url)
        val cleanedToken = token.trim()
        prefs.edit()
            .putString(KEY_BACKEND_URL, cleanedUrl)
            .putString(KEY_UKRAINE_ALARM_TOKEN, cleanedToken)
            .putBoolean(KEY_SERVER_CONFIGURED, markConfigured && cleanedUrl.isNotEmpty())
            .apply()
        _backendUrl.value = cleanedUrl
        _apiToken.value = cleanedToken
        _isServerConfigured.value = markConfigured && cleanedUrl.isNotEmpty()
    }

    fun saveBackendUrl(url: String) {
        saveBackendSettings(url, _apiToken.value)
    }

    fun resetServerSetup() {
        prefs.edit().putBoolean(KEY_SERVER_CONFIGURED, false).apply()
        _isServerConfigured.value = false
    }

    suspend fun updateSelectedRegion(newRegion: SelectedRegionConfig): Boolean {
        prefs.edit()
            .putString(KEY_SELECTED_REGION, newRegion.toJson())
            .putBoolean(KEY_TOPIC_SYNC_PENDING, true)
            .apply()
        _selectedRegion.value = newRegion
        _isTopicSyncPending.value = true
        ensureTopicSubscriptions(newRegion)
        PhoneWearMessageSender(appContext).syncSelectedRegion(newRegion)
        if (_isServerConfigured.value) {
            fetchCurrentAlertStatus(newRegion)
        }
        return true
    }

    suspend fun fetchRegionsFromBackend(forceRefresh: Boolean = true): List<RegionNode> =
        withContext(Dispatchers.IO) {
            val baseUrl = _backendUrl.value.trim().trimEnd('/')
            if (baseUrl.isEmpty()) {
                return@withContext _regionsTree.value
            }

            _regionsSourceState.value = _regionsSourceState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            val token = _apiToken.value.trim()
            val directApi = isDirectUkraineAlarmApi(baseUrl)

            if (directApi && token.isEmpty()) {
                val msg = "Потрібен API-токен UkraineAlarm для прямого запиту api.ukrainealarm.com"
                _regionsSourceState.value = RegionsSourceState(
                    isLoading = false,
                    isLiveFromApi = prefs.contains(KEY_CACHED_REGIONS_JSON),
                    stateCount = _regionsTree.value.size,
                    errorMessage = msg
                )
                return@withContext _regionsTree.value
            }

            val endpoint = if (directApi) {
                "$baseUrl/api/v3/regions"
            } else {
                val suffix = if (forceRefresh) "?refresh=true" else ""
                "$baseUrl/api/v1/regions$suffix"
            }

            try {
                val (code, body) = executeGet(
                    endpoint = endpoint,
                    authHeader = if (directApi) token else null
                )
                if (code in 200..299 && body != null) {
                    val parsed = RegionNode.parseStatesList(body)
                    if (parsed.isNotEmpty()) {
                        prefs.edit().putString(KEY_CACHED_REGIONS_JSON, body).apply()
                        _regionsTree.value = parsed
                        _regionsSourceState.value = RegionsSourceState(
                            isLoading = false,
                            isLiveFromApi = true,
                            stateCount = parsed.size,
                            errorMessage = null
                        )
                        return@withContext parsed
                    }
                }
                val errMsg = when (code) {
                    401, 403 -> "Доступ заборонено ($code). Перевірте API-токен або налаштування сервера"
                    else -> "Сервер повернув HTTP $code"
                }
                Log.w(TAG, "Failed to load regions from $endpoint: HTTP $code")
                _regionsSourceState.value = RegionsSourceState(
                    isLoading = false,
                    isLiveFromApi = prefs.contains(KEY_CACHED_REGIONS_JSON),
                    stateCount = _regionsTree.value.size,
                    errorMessage = errMsg
                )
            } catch (e: Exception) {
                Log.w(TAG, "Unable to reach $endpoint: ${e.message}")
                _regionsSourceState.value = RegionsSourceState(
                    isLoading = false,
                    isLiveFromApi = prefs.contains(KEY_CACHED_REGIONS_JSON),
                    stateCount = _regionsTree.value.size,
                    errorMessage = "Сервер недоступний"
                )
            }

            return@withContext _regionsTree.value
        }

    suspend fun fetchCurrentAlertStatus(
        region: SelectedRegionConfig = _selectedRegion.value
    ): Result<AlertEvent> = withContext(Dispatchers.IO) {
        val baseUrl = _backendUrl.value.trim().trimEnd('/')
        if (baseUrl.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Сервер тривог ще не налаштовано"))
        }

        val token = _apiToken.value.trim()
        val directApi = isDirectUkraineAlarmApi(baseUrl)

        if (directApi && token.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Вкажіть API-токен UkraineAlarm у Налаштуваннях")
            )
        }

        try {
            if (directApi) {
                val idsToCheck = region.allSubscribedIds().asReversed()
                var matchedActiveEvent: AlertEvent? = null

                for (regionId in idsToCheck) {
                    val (code, body) = executeGet(
                        endpoint = "$baseUrl/api/v3/alerts/$regionId",
                        authHeader = token
                    )
                    if (code !in 200..299 || body == null) {
                        return@withContext Result.failure(IllegalStateException("API повернуло HTTP $code"))
                    }
                    val parsed = parseDirectUkraineAlarmAlerts(body, region)
                    if (parsed != null && parsed.isActive) {
                        matchedActiveEvent = parsed
                        break
                    }
                }

                val finalEvent = matchedActiveEvent ?: buildAllClearEvent(region)
                syncAlertStateAndNotifyWatch(finalEvent)
                return@withContext Result.success(finalEvent)
            } else {
                val (code, body) = executeGet(
                    endpoint = "$baseUrl/api/v1/alerts/${region.activeRegionId}",
                    authHeader = null
                )
                if (code !in 200..299 || body == null) {
                    return@withContext Result.failure(IllegalStateException("Сервер повернув HTTP $code"))
                }
                val finalEvent = parseBackendAlertStatus(body, region)
                syncAlertStateAndNotifyWatch(finalEvent)
                return@withContext Result.success(finalEvent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching active alert status: ${e.message}")
            return@withContext Result.failure(IllegalStateException("Сервер недоступний"))
        }
    }

    private suspend fun syncAlertStateAndNotifyWatch(finalEvent: AlertEvent) {
        if (AlertTransitionPolicy.isIgnoredAlarmType(finalEvent.alarmType) ||
            finalEvent.threatType == ThreatType.INFO
        ) {
            return
        }

        val previous = AirAlertFirebaseMessagingService.currentAlertState.value
        val watchAction = AlertTransitionPolicy.evaluate(previous, finalEvent)

        AirAlertFirebaseMessagingService.updateCurrentAlertState(finalEvent)
        val wearSender = PhoneWearMessageSender(appContext)
        when (watchAction) {
            WatchAction.TRIGGER_ALARM, WatchAction.TRIGGER_CLEAR -> wearSender.sendAlertTrigger(finalEvent)
            WatchAction.SYNC_PASSIVE -> wearSender.syncAlertStatus(finalEvent)
            WatchAction.NONE -> Unit
        }
    }

    private fun parseDirectUkraineAlarmAlerts(
        jsonBody: String,
        selected: SelectedRegionConfig
    ): AlertEvent? {
        val trimmed = jsonBody.trim()
        if (!trimmed.startsWith("[")) return null
        val regionsArray = JSONArray(trimmed)
        val aggregatedLevels = mutableListOf<ActiveAlertLevel>()
        var resolvedRegionId = selected.activeRegionId
        var resolvedRegionName = selected.activeRegionName

        for (i in 0 until regionsArray.length()) {
            val regionObj = regionsArray.optJSONObject(i) ?: continue
            val activeAlerts = regionObj.optJSONArray("activeAlerts") ?: continue
            if (activeAlerts.length() == 0) continue

            val regionIdFromObj = regionObj.optString("regionId", "").trim()
            val regionNameFromObj = regionObj.optString("regionName", "").trim()
            if (regionIdFromObj.isNotEmpty()) resolvedRegionId = regionIdFromObj
            if (regionNameFromObj.isNotEmpty()) resolvedRegionName = regionNameFromObj

            for (j in 0 until activeAlerts.length()) {
                val alertObj = activeAlerts.optJSONObject(j) ?: continue
                val rawType = alertObj.optString("type")
                    .ifBlank { alertObj.optString("alertType", "AIR") }
                    .trim()
                    .uppercase()
                if (AlertTransitionPolicy.isIgnoredAlarmType(rawType)) continue

                val threat = ThreatType.fromApiType(rawType)
                val levelStr = alertObj.optString("alertLevel", "")
                val level = if (levelStr.isNotBlank()) {
                    ActiveAlertLevel.parseDangerLevel(levelStr)
                } else {
                    threat.defaultDangerLevel
                }
                aggregatedLevels.add(
                    ActiveAlertLevel(
                        alertLevel = level,
                        reason = alertObj.optString("reason", "").trim(),
                        createdAt = alertObj.optString("lastUpdate")
                            .ifBlank { alertObj.optString("startDate", "") }
                            .trim(),
                        alarmType = rawType
                    )
                )
            }
        }

        if (aggregatedLevels.isEmpty()) return null
        val primaryLevel = aggregatedLevels.firstOrNull { it.alertLevel == DangerLevel.RED } ?: aggregatedLevels.first()
        val primaryThreat = ThreatType.fromApiType(primaryLevel.alarmType)
        val overallDanger = AlertEvent.computeOverallDangerLevel(true, aggregatedLevels, primaryThreat)

        return AlertEvent(
            regionId = resolvedRegionId,
            threatType = primaryThreat,
            dangerLevel = overallDanger,
            region = resolvedRegionName,
            title = primaryThreat.ukrainianLabel,
            text = primaryThreat.instructionText,
            timestamp = System.currentTimeMillis(),
            isActive = true,
            alarmType = primaryLevel.alarmType,
            activeAlertLevels = aggregatedLevels,
            eventReason = primaryLevel.reason
        )
    }

    private fun parseBackendAlertStatus(
        jsonBody: String,
        selected: SelectedRegionConfig
    ): AlertEvent {
        val obj = JSONObject(jsonBody)
        val rawAlertType = obj.optString("alarmType")
            .ifBlank { obj.optString("alertType", "AIR") }
        val levelsJson = obj.optJSONArray("activeAlertLevels")?.toString()
        val parsedLevels = ActiveAlertLevel.fromJsonArray(levelsJson, rawAlertType)

        val isActive = if (obj.has("activeAlertLevels")) {
            parsedLevels.isNotEmpty()
        } else {
            obj.optBoolean("isActive", false)
        }

        val regionId = obj.optString("regionId", selected.activeRegionId)
        val regionName = obj.optString("regionName").ifBlank { selected.activeRegionName }
        val threat = if (!isActive) ThreatType.ALL_CLEAR else ThreatType.fromApiType(rawAlertType)
        val dangerLevel = if (!isActive) {
            DangerLevel.GREEN
        } else {
            obj.optString("dangerLevel", "").takeIf { it.isNotBlank() }?.let {
                ActiveAlertLevel.parseDangerLevel(it)
            } ?: AlertEvent.computeOverallDangerLevel(true, parsedLevels, threat)
        }

        return AlertEvent(
            regionId = regionId,
            threatType = threat,
            dangerLevel = dangerLevel,
            region = regionName,
            title = threat.ukrainianLabel,
            text = threat.instructionText,
            timestamp = System.currentTimeMillis(),
            isActive = isActive && threat != ThreatType.ALL_CLEAR && threat != ThreatType.INFO,
            alarmType = threat.apiCode,
            activeAlertLevels = if (isActive) parsedLevels else emptyList()
        )
    }

    private fun buildAllClearEvent(region: SelectedRegionConfig): AlertEvent {
        return AlertEvent(
            regionId = region.activeRegionId,
            threatType = ThreatType.ALL_CLEAR,
            dangerLevel = DangerLevel.GREEN,
            region = region.activeRegionName,
            title = "Тривоги немає",
            text = "У обраному регіоні наразі безпечно",
            timestamp = System.currentTimeMillis(),
            isActive = false
        )
    }

    private fun executeGet(endpoint: String, authHeader: String?): Pair<Int, String?> {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 3000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            if (!authHeader.isNullOrBlank()) {
                setRequestProperty("Authorization", authHeader)
            }
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } }
            code to body
        } finally {
            conn.disconnect()
        }
    }

    suspend fun syncPendingTopicSubscriptionsIfNeeded(): Boolean {
        val region = _selectedRegion.value
        val desiredTopics = region.allSubscribedIds().map { "region_$it" }.toSet()
        val syncedTopics = prefs.getStringSet(KEY_SYNCED_FCM_TOPICS, emptySet()) ?: emptySet()
        val pending = prefs.getBoolean(KEY_TOPIC_SYNC_PENDING, false)
        if (!pending && syncedTopics == desiredTopics) {
            return true
        }
        return ensureTopicSubscriptions(region)
    }

    suspend fun ensureTopicSubscriptions(
        region: SelectedRegionConfig = _selectedRegion.value,
        forceResubscribe: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        topicSyncMutex.withLock {
            val messaging = try {
                FirebaseMessaging.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "FirebaseMessaging unavailable for topic sync: ${e.message}")
                markTopicSyncPending(true)
                scheduleTopicRetryLoop()
                return@withLock false
            }

            val desiredTopics = region.allSubscribedIds().map { "region_$it" }.toSet()
            val syncedTopics = (prefs.getStringSet(KEY_SYNCED_FCM_TOPICS, emptySet()) ?: emptySet())
                .toMutableSet()

            var allSucceeded = true

            // Unsubscribe from topics that belong to previously selected regions
            val staleTopics = syncedTopics - desiredTopics
            for (topic in staleTopics) {
                val ok = executeTopicOpWithRetry("unsubscribe:$topic") {
                    messaging.unsubscribeFromTopic(topic).await()
                }
                if (ok) {
                    syncedTopics.remove(topic)
                    saveSyncedTopics(syncedTopics)
                } else {
                    allSucceeded = false
                }
            }

            // Subscribe to missing topics (or all desired topics on forced refresh/token rotation)
            val topicsToSubscribe = if (forceResubscribe) desiredTopics else (desiredTopics - syncedTopics)
            for (topic in topicsToSubscribe) {
                val ok = executeTopicOpWithRetry("subscribe:$topic") {
                    messaging.subscribeToTopic(topic).await()
                }
                if (ok) {
                    syncedTopics.add(topic)
                    saveSyncedTopics(syncedTopics)
                } else {
                    allSucceeded = false
                }
            }

            markTopicSyncPending(!allSucceeded)
            if (allSucceeded) {
                topicRetryJob?.cancel()
                topicRetryJob = null
            } else {
                scheduleTopicRetryLoop()
            }
            allSucceeded
        }
    }

    private suspend fun executeTopicOpWithRetry(
        label: String,
        maxAttempts: Int = 3,
        block: suspend () -> Unit
    ): Boolean {
        val retryDelaysMs = longArrayOf(1_000L, 3_000L)
        for (attempt in 1..maxAttempts) {
            try {
                block()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "FCM $label failed (attempt $attempt/$maxAttempts): ${e.message}")
                if (attempt < maxAttempts) {
                    delay(retryDelaysMs.getOrElse(attempt - 1) { 3_000L })
                }
            }
        }
        return false
    }

    private fun scheduleTopicRetryLoop() {
        if (topicRetryJob?.isActive == true) return
        topicRetryJob = clientScope.launch {
            val backoffScheduleMs = longArrayOf(5_000L, 15_000L, 45_000L, 120_000L)
            for (waitMs in backoffScheduleMs) {
                delay(waitMs)
                if (!prefs.getBoolean(KEY_TOPIC_SYNC_PENDING, false)) {
                    return@launch
                }
                val ok = ensureTopicSubscriptions(_selectedRegion.value)
                if (ok) {
                    return@launch
                }
            }
        }
    }

    private fun saveSyncedTopics(topics: Set<String>) {
        prefs.edit().putStringSet(KEY_SYNCED_FCM_TOPICS, topics.toSet()).apply()
    }

    private fun markTopicSyncPending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_TOPIC_SYNC_PENDING, pending).apply()
        _isTopicSyncPending.value = pending
    }

    companion object {
        private const val TAG = "BackendClient"
        private const val EXPECTED_SERVICE_ID = "air-alert-backend"
        private const val PREFS_NAME = "air_alert_backend_prefs"
        private const val KEY_SELECTED_REGION = "selected_region_json"
        private const val KEY_BACKEND_URL = "backend_url"
        private const val KEY_UKRAINE_ALARM_TOKEN = "ukraine_alarm_api_token"
        private const val KEY_CACHED_REGIONS_JSON = "cached_regions_json"
        private const val KEY_SERVER_CONFIGURED = "is_server_configured"
        private const val KEY_SYNCED_FCM_TOPICS = "synced_fcm_topics"
        private const val KEY_TOPIC_SYNC_PENDING = "topic_sync_pending"
        const val UKRAINE_ALARM_API_URL = "https://api.ukrainealarm.com"

        @Volatile
        private var INSTANCE: BackendClient? = null

        fun normalizeUrl(rawUrl: String): String {
            var cleaned = rawUrl.trim()
            if (cleaned.isEmpty()) return ""
            if (!cleaned.startsWith("http://", ignoreCase = true) &&
                !cleaned.startsWith("https://", ignoreCase = true)
            ) {
                cleaned = if (cleaned.contains("api.ukrainealarm.com", ignoreCase = true)) {
                    "https://$cleaned"
                } else {
                    "http://$cleaned"
                }
            }
            cleaned = cleaned.trimEnd('/')
            if (cleaned.endsWith("/api/v1", ignoreCase = true)) {
                cleaned = cleaned.dropLast("/api/v1".length)
            }
            return cleaned.trimEnd('/')
        }

        fun validatePingJson(
            body: String,
            expectedChallenge: String,
            normalizedUrl: String,
            latencyMs: Long
        ): Result<ServerPingInfo> {
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                return Result.failure(
                    IllegalStateException("Цей сервер не є сервером Air Alert (відповідь не JSON)")
                )
            }

            val service = json.optString("service", "")
            val status = json.optString("status", "")
            val apiVersion = json.optInt("apiVersion", 0)
            val echoedChallenge = json.optString("challenge", "")
            val regionsCount = json.optInt("regionsCount", 0)
            val uaConfigured = json.optBoolean("ukraineAlarmConfigured", false)

            if (service != EXPECTED_SERVICE_ID || status != "ok" || apiVersion < 1 || echoedChallenge != expectedChallenge) {
                return Result.failure(
                    IllegalStateException("Перевірку не пройдено: це сторонній сервер, не Air Alert Backend")
                )
            }

            return Result.success(
                ServerPingInfo(
                    normalizedUrl = normalizedUrl,
                    serviceName = service,
                    apiVersion = apiVersion,
                    ukraineAlarmConfigured = uaConfigured,
                    regionsCount = regionsCount,
                    latencyMs = latencyMs
                )
            )
        }

        fun getInstance(context: Context): BackendClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BackendClient(context).also { INSTANCE = it }
            }
        }
    }
}
