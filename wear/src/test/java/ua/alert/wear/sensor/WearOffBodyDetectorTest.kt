package ua.alert.wear.sensor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearOffBodyDetectorTest {

    @Test
    fun zeroSensorValueReportsOffWrist() {
        assertFalse(WearOffBodyDetector.isOnBodyReading(0.0f))
        assertFalse(WearOffBodyDetector.isOnBodyReading(-0.0f))
        assertFalse(WearOffBodyDetector.isOnBodyReading(Float.NaN))
    }

    @Test
    fun nonZeroSensorValueReportsOnWrist() {
        assertTrue(WearOffBodyDetector.isOnBodyReading(1.0f))
    }
}
