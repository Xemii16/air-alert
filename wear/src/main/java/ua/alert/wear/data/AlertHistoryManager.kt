package ua.alert.wear.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import ua.alert.shared.AlertEvent

class AlertHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _historyFlow = MutableStateFlow(loadHistory())
    val historyFlow: StateFlow<List<AlertEvent>> = _historyFlow.asStateFlow()

    private fun loadHistory(): List<AlertEvent> {
        val jsonStr = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<AlertEvent>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(AlertEvent.fromJson(array.getString(i)))
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
        return list
    }

    fun addAlert(event: AlertEvent) {
        val current = _historyFlow.value.toMutableList()
        current.add(0, event)
        val trimmed = if (current.size > MAX_ITEMS) current.take(MAX_ITEMS) else current

        val array = JSONArray()
        for (item in trimmed) {
            array.put(item.toJson())
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
        _historyFlow.value = trimmed
    }

    companion object {
        private const val PREFS_NAME = "air_alert_wear_history"
        private const val KEY_HISTORY = "history_json"
        private const val MAX_ITEMS = 15

        @Volatile
        private var instance: AlertHistoryManager? = null

        fun getInstance(context: Context): AlertHistoryManager {
            return instance ?: synchronized(this) {
                instance ?: AlertHistoryManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
