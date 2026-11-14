package ua.alert.shared

import org.json.JSONArray
import org.json.JSONObject

data class ActiveAlertLevel(
    val alertLevel: DangerLevel = DangerLevel.RED,
    val reason: String = "",
    val createdAt: String = "",
    val alarmType: String = "AIR"
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("alertLevel", if (alertLevel == DangerLevel.YELLOW) "Yellow" else "Red")
            put("reason", reason)
            put("createdAt", createdAt)
            put("alarmType", alarmType)
        }
    }

    /**
     * Order-independent identity key for comparing active threat snapshots.
     */
    fun identityKey(): String {
        return "${alarmType.trim().uppercase()}|${alertLevel.name}|${reason.trim()}"
    }

    companion object {
        fun parseDangerLevel(raw: String?): DangerLevel {
            return when (raw?.trim()?.uppercase()) {
                "YELLOW" -> DangerLevel.YELLOW
                "GREEN", "NONE", "ALL_CLEAR" -> DangerLevel.GREEN
                else -> DangerLevel.RED
            }
        }

        fun fromJsonObject(obj: JSONObject, fallbackAlarmType: String = "AIR"): ActiveAlertLevel {
            val level = parseDangerLevel(obj.optString("alertLevel", "Red"))
            val reason = obj.optString("reason", "").trim()
            val createdAt = obj.optString("createdAt", "").trim()
            val alarmType = obj.optString("alarmType", fallbackAlarmType)
                .trim()
                .ifBlank { fallbackAlarmType }
                .uppercase()
            return ActiveAlertLevel(
                alertLevel = level,
                reason = reason,
                createdAt = createdAt,
                alarmType = alarmType
            )
        }

        fun toJsonArray(levels: List<ActiveAlertLevel>): String {
            val array = JSONArray()
            for (level in levels) {
                array.put(level.toJsonObject())
            }
            return array.toString()
        }

        fun fromJsonArray(rawJson: String?, fallbackAlarmType: String = "AIR"): List<ActiveAlertLevel> {
            if (rawJson.isNullOrBlank()) return emptyList()
            val trimmed = rawJson.trim()
            if (!trimmed.startsWith("[")) return emptyList()
            return try {
                val arr = JSONArray(trimmed)
                val result = ArrayList<ActiveAlertLevel>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val item = fromJsonObject(obj, fallbackAlarmType)
                    if (!item.alarmType.equals("INFO", ignoreCase = true)) {
                        result.add(item)
                    }
                }
                result
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
