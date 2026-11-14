package ua.alert.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEventTest {

    @Test
    fun testAlertEventSerialization_redAirRaid() {
        val event = AlertEvent(
            regionId = "31",
            threatType = ThreatType.AIR_RAID,
            dangerLevel = DangerLevel.RED,
            region = "м. Київ",
            title = "Повітряна тривога!",
            text = "Негайно прямуйте до найближчого укриття!",
            timestamp = 1700000000000L,
            isActive = true
        )

        val json = event.toJson()
        val parsed = AlertEvent.fromJson(json)

        assertEquals(event.id, parsed.id)
        assertEquals("31", parsed.regionId)
        assertEquals(ThreatType.AIR_RAID, parsed.threatType)
        assertEquals(DangerLevel.RED, parsed.dangerLevel)
        assertEquals("м. Київ", parsed.region)
        assertEquals("Повітряна тривога!", parsed.title)
        assertEquals("Негайно прямуйте до найближчого укриття!", parsed.text)
        assertEquals(1700000000000L, parsed.timestamp)
        assertTrue(parsed.isActive)
    }

    @Test
    fun testThreatTypeFromApiType() {
        assertEquals(ThreatType.AIR_RAID, ThreatType.fromApiType("AIR"))
        assertEquals(ThreatType.AIR_RAID, ThreatType.fromApiType("air_raid"))
        assertEquals(ThreatType.ARTILLERY, ThreatType.fromApiType("ARTILLERY"))
        assertEquals(ThreatType.URBAN_FIGHTS, ThreatType.fromApiType("urban_fights"))
        assertEquals(ThreatType.CHEMICAL, ThreatType.fromApiType("CHEMICAL"))
        assertEquals(ThreatType.NUCLEAR, ThreatType.fromApiType("nuclear"))
        assertEquals(ThreatType.INFO, ThreatType.fromApiType("INFO"))
        assertEquals(ThreatType.ALL_CLEAR, ThreatType.fromApiType("ALL_CLEAR"))
    }

    @Test
    fun testAlertEventSerialization_multipleLevelsAndReasons() {
        val levels = listOf(
            ActiveAlertLevel(DangerLevel.YELLOW, "UAV activity", "2026-09-07T11:42:00Z", "AIR"),
            ActiveAlertLevel(DangerLevel.RED, "Missile threat", "2026-09-07T11:55:00Z", "AIR"),
            ActiveAlertLevel(DangerLevel.RED, "Ballistic missile threat", "2026-09-07T12:05:00Z", "AIR")
        )
        val event = AlertEvent(
            regionId = "14",
            threatType = ThreatType.AIR_RAID,
            dangerLevel = DangerLevel.RED,
            region = "Київська область",
            isActive = true,
            alarmType = "AIR",
            activeAlertLevels = levels,
            eventStatus = "Activate",
            eventReason = "Ballistic missile threat"
        )

        val parsed = AlertEvent.fromJson(event.toJson())
        assertEquals(3, parsed.activeAlertLevels.size)
        assertEquals(DangerLevel.RED, parsed.dangerLevel)
        assertEquals("Activate", parsed.eventStatus)
        assertEquals("Ballistic missile threat", parsed.eventReason)
        assertEquals(
            listOf("Missile threat", "Ballistic missile threat", "UAV activity"),
            parsed.displayReasons()
        )
    }

    @Test
    fun testAlertTransitionPolicyRules() {
        val infoEvent = AlertEvent(
            regionId = "14",
            threatType = ThreatType.INFO,
            dangerLevel = DangerLevel.YELLOW,
            alarmType = "INFO",
            isActive = true
        )
        assertEquals(WatchAction.NONE, AlertTransitionPolicy.evaluate(null, infoEvent))

        val yellowOnly = AlertEvent(
            regionId = "14",
            threatType = ThreatType.AIR_RAID,
            dangerLevel = DangerLevel.YELLOW,
            isActive = true,
            activeAlertLevels = listOf(ActiveAlertLevel(DangerLevel.YELLOW, "UAV activity", "", "AIR"))
        )
        assertEquals(WatchAction.TRIGGER_ALARM, AlertTransitionPolicy.evaluate(null, yellowOnly))

        val escalatedRed = yellowOnly.copy(
            dangerLevel = DangerLevel.RED,
            activeAlertLevels = yellowOnly.activeAlertLevels +
                ActiveAlertLevel(DangerLevel.RED, "Missile threat", "", "AIR")
        )
        assertEquals(WatchAction.TRIGGER_ALARM, AlertTransitionPolicy.evaluate(yellowOnly, escalatedRed))

        val secondRedAdded = escalatedRed.copy(
            activeAlertLevels = escalatedRed.activeAlertLevels +
                ActiveAlertLevel(DangerLevel.RED, "Ballistic missile threat", "", "AIR"),
            eventReason = "Ballistic missile threat"
        )
        assertEquals(WatchAction.TRIGGER_ALARM, AlertTransitionPolicy.evaluate(escalatedRed, secondRedAdded))

        // Partial DEACTIVATE keeps threat active -> SYNC_PASSIVE, not CLEAR
        assertEquals(WatchAction.SYNC_PASSIVE, AlertTransitionPolicy.evaluate(secondRedAdded, escalatedRed))
        assertEquals(WatchAction.SYNC_PASSIVE, AlertTransitionPolicy.evaluate(escalatedRed, yellowOnly))

        // Same items in reversed order -> NONE
        val reversedEscalated = escalatedRed.copy(activeAlertLevels = escalatedRed.activeAlertLevels.reversed())
        assertEquals(WatchAction.NONE, AlertTransitionPolicy.evaluate(escalatedRed, reversedEscalated))

        // Empty snapshot -> TRIGGER_CLEAR
        val allClear = AlertEvent(
            regionId = "14",
            threatType = ThreatType.ALL_CLEAR,
            dangerLevel = DangerLevel.GREEN,
            isActive = false,
            activeAlertLevels = emptyList()
        )
        assertEquals(WatchAction.TRIGGER_CLEAR, AlertTransitionPolicy.evaluate(yellowOnly, allClear))
    }

    @Test
    fun testAlertEventSerialization_allClear() {
        val event = AlertEvent(
            regionId = "669",
            threatType = ThreatType.ALL_CLEAR,
            dangerLevel = DangerLevel.GREEN,
            region = "Ірпінська територіальна громада",
            title = "Відбій тривоги",
            text = "Увага! Відбій повітряної тривоги",
            isActive = false
        )

        val json = event.toJson()
        val parsed = AlertEvent.fromJson(json)

        assertEquals("669", parsed.regionId)
        assertEquals(ThreatType.ALL_CLEAR, parsed.threatType)
        assertEquals(DangerLevel.GREEN, parsed.dangerLevel)
        assertFalse(parsed.isActive)
    }

    @Test
    fun testSelectedRegionConfigSerialization() {
        val config = SelectedRegionConfig(
            stateId = "14",
            stateName = "Київська область",
            districtId = "64",
            districtName = "Бучанський район",
            communityId = "669",
            communityName = "Ірпінська територіальна громада"
        )
        assertEquals("669", config.activeRegionId)
        assertEquals("Ірпінська територіальна громада", config.activeRegionName)
        assertEquals(listOf("14", "64", "669"), config.allSubscribedIds())

        val parsed = SelectedRegionConfig.fromJson(config.toJson())
        assertEquals(config, parsed)
    }
}
