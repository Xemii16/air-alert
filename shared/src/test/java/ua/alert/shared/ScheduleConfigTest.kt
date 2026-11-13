package ua.alert.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConfigTest {

    @Test
    fun testDisabledSchedule_alwaysActive() {
        val config = ScheduleConfig(isEnabled = false, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        assertTrue(config.isWithinActiveHours(12, 0))
        assertTrue(config.isWithinActiveHours(2, 30))
        assertTrue(config.isWithinActiveHours(23, 59))
    }

    @Test
    fun testDaytimeSchedule() {
        // Active from 08:30 to 20:00
        val config = ScheduleConfig(isEnabled = true, startHour = 8, startMinute = 30, endHour = 20, endMinute = 0)
        
        assertFalse("Before start time", config.isWithinActiveHours(8, 29))
        assertTrue("Exact start time", config.isWithinActiveHours(8, 30))
        assertTrue("Middle of day", config.isWithinActiveHours(14, 0))
        assertTrue("Exact end time", config.isWithinActiveHours(20, 0))
        assertFalse("After end time", config.isWithinActiveHours(20, 1))
        assertFalse("Night time", config.isWithinActiveHours(2, 0))
    }

    @Test
    fun testOvernightSchedule() {
        // Active from 22:00 to 07:00 (e.g. night alerts while sleeping)
        val config = ScheduleConfig(isEnabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        
        assertTrue("Late night start", config.isWithinActiveHours(22, 0))
        assertTrue("Midnight", config.isWithinActiveHours(0, 0))
        assertTrue("Early morning", config.isWithinActiveHours(4, 15))
        assertTrue("Exact morning end", config.isWithinActiveHours(7, 0))
        assertFalse("After morning end", config.isWithinActiveHours(7, 1))
        assertFalse("Afternoon", config.isWithinActiveHours(15, 30))
        assertFalse("Just before evening start", config.isWithinActiveHours(21, 59))
    }

    @Test
    fun testJsonSerialization() {
        val original = ScheduleConfig(isEnabled = true, startHour = 23, startMinute = 15, endHour = 6, endMinute = 45)
        val json = original.toJson()
        val parsed = ScheduleConfig.fromJson(json)

        assertEquals(original.isEnabled, parsed.isEnabled)
        assertEquals(original.startHour, parsed.startHour)
        assertEquals(original.startMinute, parsed.startMinute)
        assertEquals(original.endHour, parsed.endHour)
        assertEquals(original.endMinute, parsed.endMinute)
    }
}
