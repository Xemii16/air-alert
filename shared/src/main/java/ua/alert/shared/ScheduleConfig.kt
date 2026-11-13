package ua.alert.shared

import org.json.JSONObject

data class ScheduleConfig(
    val isEnabled: Boolean = false,
    val startHour: Int = 22,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0
) {
    /**
     * Determines whether an alert arriving at [currentHour]:[currentMinute]
     * is within the active ringing hours.
     * If schedule is disabled ([isEnabled] == false), always returns true (24/7 active).
     */
    fun isWithinActiveHours(currentHour: Int, currentMinute: Int): Boolean {
        if (!isEnabled) return true

        val currentMinutesOfDay = currentHour * 60 + currentMinute
        val startMinutesOfDay = startHour * 60 + startMinute
        val endMinutesOfDay = endHour * 60 + endMinute

        return if (startMinutesOfDay <= endMinutesOfDay) {
            // Standard day interval (e.g., 08:00 to 22:00)
            currentMinutesOfDay in startMinutesOfDay..endMinutesOfDay
        } else {
            // Overnight interval (e.g., 22:00 to 07:00)
            currentMinutesOfDay >= startMinutesOfDay || currentMinutesOfDay <= endMinutesOfDay
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("isEnabled", isEnabled)
        obj.put("startHour", startHour)
        obj.put("startMinute", startMinute)
        obj.put("endHour", endHour)
        obj.put("endMinute", endMinute)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): ScheduleConfig {
            return try {
                val obj = JSONObject(jsonStr)
                ScheduleConfig(
                    isEnabled = obj.optBoolean("isEnabled", false),
                    startHour = obj.optInt("startHour", 22),
                    startMinute = obj.optInt("startMinute", 0),
                    endHour = obj.optInt("endHour", 7),
                    endMinute = obj.optInt("endMinute", 0)
                )
            } catch (e: Exception) {
                ScheduleConfig()
            }
        }
    }
}
