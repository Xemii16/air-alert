package ua.alert.shared

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
    val isActive: Boolean = threatType != ThreatType.ALL_CLEAR
) {
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
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): AlertEvent {
            val obj = JSONObject(jsonStr)
            val threat = ThreatType.fromApiType(obj.optString("threatType", ThreatType.AIR_RAID.name))
            val danger = try {
                DangerLevel.valueOf(obj.optString("dangerLevel", threat.defaultDangerLevel.name))
            } catch (e: Exception) {
                threat.defaultDangerLevel
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
                isActive = obj.optBoolean("isActive", threat != ThreatType.ALL_CLEAR)
            )
        }
    }
}
