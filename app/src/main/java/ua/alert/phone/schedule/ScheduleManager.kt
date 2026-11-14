package ua.alert.phone.schedule

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ua.alert.shared.ScheduleConfig

/**
 * Alerts are always active 24/7 (schedule gating removed).
 * Kept lightweight so any legacy references resolve cleanly without schedule filtering.
 */
class ScheduleManager private constructor() {

    private val _scheduleState = MutableStateFlow(ScheduleConfig(isEnabled = false))
    val scheduleState: StateFlow<ScheduleConfig> = _scheduleState.asStateFlow()

    fun isAlertPermittedNow(): Boolean = true

    companion object {
        @Volatile
        private var INSTANCE: ScheduleManager? = null

        fun getInstance(@Suppress("UNUSED_PARAMETER") context: Context): ScheduleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScheduleManager().also { INSTANCE = it }
            }
        }
    }
}
