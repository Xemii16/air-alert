package ua.alert.phone.schedule

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.alert.shared.ScheduleConfig
import java.util.Calendar

class ScheduleManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _scheduleState = MutableStateFlow(loadSchedule())
    val scheduleState: StateFlow<ScheduleConfig> = _scheduleState.asStateFlow()

    private fun loadSchedule(): ScheduleConfig {
        val json = prefs.getString(KEY_SCHEDULE_JSON, null)
        return if (json != null) {
            ScheduleConfig.fromJson(json)
        } else {
            ScheduleConfig(isEnabled = false, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        }
    }

    fun saveSchedule(config: ScheduleConfig) {
        prefs.edit().putString(KEY_SCHEDULE_JSON, config.toJson()).apply()
        _scheduleState.value = config
    }

    fun isAlertPermittedNow(): Boolean {
        val config = _scheduleState.value
        if (!config.isEnabled) {
            return true
        }

        val calendar = Calendar.getInstance()
        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(Calendar.MINUTE)

        return config.isWithinActiveHours(currentHour, currentMinute)
    }

    companion object {
        private const val PREFS_NAME = "air_alert_schedule_prefs"
        private const val KEY_SCHEDULE_JSON = "schedule_config_json"

        @Volatile
        private var instance: ScheduleManager? = null

        fun getInstance(context: Context): ScheduleManager {
            return instance ?: synchronized(this) {
                instance ?: ScheduleManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
