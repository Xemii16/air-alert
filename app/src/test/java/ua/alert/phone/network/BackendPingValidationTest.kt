package ua.alert.phone.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendPingValidationTest {

    @Test
    fun normalizeUrl_addsHttpSchemeAndStripsApiSuffix() {
        assertEquals("http://192.168.1.50:8080", BackendClient.normalizeUrl("192.168.1.50:8080/"))
        assertEquals("https://alerts.example.com", BackendClient.normalizeUrl("https://alerts.example.com/api/v1/"))
        assertEquals("https://api.ukrainealarm.com", BackendClient.normalizeUrl("api.ukrainealarm.com"))
    }

    @Test
    fun validatePingJson_acceptsAuthenticBackendWithMatchingChallenge() {
        val json = """
            {
              "service": "air-alert-backend",
              "status": "ok",
              "apiVersion": 1,
              "ukraineAlarmConfigured": true,
              "regionsCount": 26,
              "challenge": "nonce-abc-123"
            }
        """.trimIndent()

        val result = BackendClient.validatePingJson(
            body = json,
            expectedChallenge = "nonce-abc-123",
            normalizedUrl = "http://192.168.1.50:8080",
            latencyMs = 14L
        )
        assertTrue(result.isSuccess)
        val info = result.getOrThrow()
        assertEquals("air-alert-backend", info.serviceName)
        assertEquals(26, info.regionsCount)
        assertTrue(info.ukraineAlarmConfigured)
    }

    @Test
    fun validatePingJson_rejectsWrongServiceOrMismatchedChallengeOrHtml() {
        val foreignJson = """{"service":"other-app","status":"ok","apiVersion":1,"challenge":"nonce-abc-123"}"""
        assertTrue(
            BackendClient.validatePingJson(foreignJson, "nonce-abc-123", "http://x", 10L).isFailure
        )

        val staleChallengeJson = """{"service":"air-alert-backend","status":"ok","apiVersion":1,"challenge":"stale"}"""
        assertTrue(
            BackendClient.validatePingJson(staleChallengeJson, "nonce-abc-123", "http://x", 10L).isFailure
        )

        assertTrue(
            BackendClient.validatePingJson("<html><body>nginx</body></html>", "nonce-abc-123", "http://x", 10L).isFailure
        )
    }
}
