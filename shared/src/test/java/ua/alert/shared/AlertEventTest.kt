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
        assertEquals(ThreatType.ALL_CLEAR, ThreatType.fromApiType("ALL_CLEAR"))
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
