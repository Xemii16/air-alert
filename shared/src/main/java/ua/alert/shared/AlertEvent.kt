package ua.alert.shared

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class AlertEvent(
    val id: String = UUID.randomUUID().toString(),
    val regionId: String = "31",
    val threatType: ThreatType = ThreatType.AIR_RAID,
    val dangerLevel: DangerLevel = threatType.defaultDangerLevel,
    val region: String = "м. Київ",
    val title: String = threatType.ukrainianLabel,
    val text: String = threatType.instructionText,
    val timestamp: Long = System.currentTimeMillis(),
    val isActive: Boolean = threatType != ThreatType.ALL_CLEAR,
    val alarmType: String = threatType.apiCode,
    val activeAlertLevels: List<ActiveAlertLevel> = emptyList(),
    val eventStatus: String = "",
    val eventReason: String = ""
) {
    /**
     * Ordered reasons suitable for compact Watch/Notification summaries without collapsing levels.
     */
    fun displayReasons(): List<String> {
        if (!isActive) return emptyList()
        val fromSnapshot = activeAlertLevels
            .sortedByDescending { it.alertLevel == DangerLevel.RED }
            .mapNotNull { item ->
                item.reason.trim().takeIf { it.isNotEmpty() }
            }
            .distinct()
        if (fromSnapshot.isNotEmpty()) {
            return fromSnapshot
        }
        val fallback = eventReason.trim()
        return if (fallback.isNotEmpty()) listOf(fallback) else emptyList()
    }

    fun hasSnapshotDifference(other: AlertEvent?): Boolean {
        if (other == null) return true
        if (isActive != other.isActive) return true
        if (dangerLevel != other.dangerLevel) return true
        if (regionId != other.regionId) return true
        if (threatType != other.threatType) return true
        return activeAlertLevels.map { it.identityKey() }.toSet() !=
            other.activeAlertLevels.map { it.identityKey() }.toSet()
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("regionId", regionId)
        obj.put("threatType", threatType.name)
        obj.put("dangerLevel", dangerLevel.name)
        obj.put("region", region)
        obj.put("title", title)
        obj.put("text", text)
        obj.put("timestamp", timestamp)
        obj.put("isActive", isActive)
        obj.put("alarmType", alarmType)
        obj.put("eventStatus", eventStatus)
        obj.put("eventReason", eventReason)
        val levelsArr = JSONArray()
        for (item in activeAlertLevels) {
            levelsArr.put(item.toJsonObject())
        }
        obj.put("activeAlertLevels", levelsArr)
        return obj.toString()
    }

    companion object {
        fun computeOverallDangerLevel(
            isActive: Boolean,
            levels: List<ActiveAlertLevel>,
            fallbackThreat: ThreatType
        ): DangerLevel {
            if (!isActive) return DangerLevel.GREEN
            if (levels.isNotEmpty()) {
                return if (levels.any { it.alertLevel == DangerLevel.RED }) {
                    DangerLevel.RED
                } else {
                    DangerLevel.YELLOW
                }
            }
            return fallbackThreat.defaultDangerLevel
        }

        fun fromJson(jsonStr: String): AlertEvent {
            val obj = JSONObject(jsonStr)
            val threat = ThreatType.fromApiType(obj.optString("threatType", ThreatType.AIR_RAID.name))
            val isActive = obj.optBoolean("isActive", threat != ThreatType.ALL_CLEAR)
            val alarmType = obj.optString("alarmType", threat.apiCode).ifBlank { threat.apiCode }
            val levelsJson = obj.optJSONArray("activeAlertLevels")?.toString()
            val parsedLevels = ActiveAlertLevel.fromJsonArray(levelsJson, alarmType)
            val danger = try {
                DangerLevel.valueOf(obj.optString("dangerLevel", ""))
            } catch (_: Exception) {
                computeOverallDangerLevel(isActive, parsedLevels, threat)
            }
            return AlertEvent(
                id = obj.optString("id", UUID.randomUUID().toString()),
                regionId = obj.optString("regionId", "31"),
                threatType = threat,
                dangerLevel = danger,
                region = obj.optString("region", "м. Київ"),
                title = obj.optString("title", threat.ukrainianLabel),
                text = obj.optString("text", threat.instructionText),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                isActive = isActive,
                alarmType = alarmType,
                activeAlertLevels = parsedLevels,
                eventStatus = obj.optString("eventStatus", ""),
                eventReason = obj.optString("eventReason", "")
            )
        }
    }
}
