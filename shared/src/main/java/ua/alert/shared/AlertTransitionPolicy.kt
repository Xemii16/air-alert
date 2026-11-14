package ua.alert.shared

enum class WatchAction {
    NONE,
    SYNC_PASSIVE,
    TRIGGER_ALARM,
    TRIGGER_CLEAR
}

/**
 * Shared transition rule for deciding whether an incoming WebHookMessageModel snapshot
 * or REST sync should trigger the Wear OS alarm, play All-Clear, silently update reasons,
 * or ignore INFO messages.
 */
object AlertTransitionPolicy {

    fun isIgnoredAlarmType(alarmType: String?): Boolean {
        return alarmType?.trim()?.equals("INFO", ignoreCase = true) == true
    }

    fun evaluate(previous: AlertEvent?, incoming: AlertEvent): WatchAction {
        if (isIgnoredAlarmType(incoming.alarmType) || incoming.threatType == ThreatType.INFO) {
            return WatchAction.NONE
        }

        if (!incoming.isActive) {
            return when {
                previous?.isActive == true -> WatchAction.TRIGGER_CLEAR
                previous == null -> WatchAction.SYNC_PASSIVE
                else -> WatchAction.NONE
            }
        }

        // Safe / uninitialized -> Active always triggers Watch full-screen alarm
        if (previous == null || !previous.isActive) {
            return WatchAction.TRIGGER_ALARM
        }

        // Escalation from Yellow to Red triggers Watch full-screen alarm
        if (previous.dangerLevel != DangerLevel.RED && incoming.dangerLevel == DangerLevel.RED) {
            return WatchAction.TRIGGER_ALARM
        }

        // Any newly added Red (alarmType, alertLevel, reason) re-triggers Watch alarm with the new reason
        val previousRedKeys = previous.activeAlertLevels
            .filter { it.alertLevel == DangerLevel.RED }
            .map { it.identityKey() }
            .toSet()

        val hasNewRedReason = incoming.activeAlertLevels.any {
            it.alertLevel == DangerLevel.RED && it.identityKey() !in previousRedKeys
        }
        if (hasNewRedReason) {
            return WatchAction.TRIGGER_ALARM
        }

        // Re-trigger if primary threatType switched while Red without snapshot items (legacy fallback)
        if (incoming.activeAlertLevels.isEmpty() &&
            incoming.dangerLevel == DangerLevel.RED &&
            previous.threatType != incoming.threatType
        ) {
            return WatchAction.TRIGGER_ALARM
        }

        // Partial DEACTIVATE or non-escalating Yellow updates silently refresh displayed reasons
        return if (incoming.hasSnapshotDifference(previous)) {
            WatchAction.SYNC_PASSIVE
        } else {
            WatchAction.NONE
        }
    }
}
