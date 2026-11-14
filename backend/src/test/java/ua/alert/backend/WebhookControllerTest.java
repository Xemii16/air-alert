package ua.alert.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import ua.alert.backend.client.UkraineAlarmApiClient;
import ua.alert.backend.controller.MobileApiController;
import ua.alert.backend.model.RegionsViewModel;
import ua.alert.backend.service.AlertStateService;
import ua.alert.backend.service.FcmPushService;
import ua.alert.backend.service.WebhookSignatureVerifier;

@SpringBootTest
@AutoConfigureMockMvc
class WebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MobileApiController mobileApiController;

    @Autowired
    private AlertStateService alertStateService;

    @Autowired
    private FcmPushService fcmPushService;

    @Autowired
    private UkraineAlarmApiClient ukraineAlarmApiClient;

    @Autowired
    private Environment environment;

    @BeforeEach
    void setUp() {
        fcmPushService.clearDispatchedPayloadsLog();
        alertStateService.clearAll();
    }

    @Test
    void testRegionsTreeEndpointReturnsHierarchy() {
        ResponseEntity<RegionsViewModel> response = mobileApiController.getRegions(false);
        assertNotNull(response.getBody());
        assertFalse(response.getBody().getStates().isEmpty());
    }

    @Test
    void testSnapshotMultiLevelPartialDeactivateAndFinalAllClearLifecycle() throws Exception {
        // 1. Activate with Yellow + two Red items (official WebHookMessageModel example)
        String activateJson = """
                {
                  "status": "Activate",
                  "regionId": 14,
                  "alarmType": "AIR",
                  "createdAt": "2026-09-07T12:05:00Z",
                  "alertLevel": "Red",
                  "reason": "Ballistic missile threat",
                  "activeAlertLevels": [
                    { "alertLevel": "Yellow", "reason": "UAV activity", "createdAt": "2026-09-07T11:42:00Z" },
                    { "alertLevel": "Red", "reason": "Missile threat", "createdAt": "2026-09-07T11:55:00Z" },
                    { "alertLevel": "Red", "reason": "Ballistic missile threat", "createdAt": "2026-09-07T12:05:00Z" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(activateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.dangerLevel").value("RED"))
                .andExpect(jsonPath("$.alertType").value("AIR"))
                .andExpect(jsonPath("$.activeAlertLevels.length()").value(3));

        // Child community 669 inherits state 14 snapshot
        assertTrue(alertStateService.hasActiveAlertForRegion("669"));
        Map<String, Object> pollAfterActivate = mobileApiController.getAlertStatus("669").getBody();
        assertNotNull(pollAfterActivate);
        assertEquals(true, pollAfterActivate.get("isActive"));
        assertEquals("RED", pollAfterActivate.get("dangerLevel"));

        // 2. Partial DEACTIVATE removes "Missile threat", leaving Yellow + one Red
        String partialDeactivateJson = """
                {
                  "status": "DEACTIVATE",
                  "regionId": 14,
                  "alarmType": "AIR",
                  "createdAt": "2026-09-07T12:18:00Z",
                  "alertLevel": "Red",
                  "reason": "Missile threat",
                  "activeAlertLevels": [
                    { "alertLevel": "Yellow", "reason": "UAV activity", "createdAt": "2026-09-07T11:42:00Z" },
                    { "alertLevel": "Red", "reason": "Ballistic missile threat", "createdAt": "2026-09-07T12:05:00Z" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(partialDeactivateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.dangerLevel").value("RED"))
                .andExpect(jsonPath("$.activeAlertLevels.length()").value(2));

        // 3. Partial DEACTIVATE removes last Red, downgrading snapshot to Yellow only (STILL active)
        String downgradeToYellowJson = """
                {
                  "status": "DEACTIVATE",
                  "regionId": 14,
                  "alarmType": "AIR",
                  "createdAt": "2026-09-07T12:25:00Z",
                  "alertLevel": "Red",
                  "reason": "Ballistic missile threat",
                  "activeAlertLevels": [
                    { "alertLevel": "Yellow", "reason": "UAV activity", "createdAt": "2026-09-07T11:42:00Z" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(downgradeToYellowJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.dangerLevel").value("YELLOW"))
                .andExpect(jsonPath("$.activeAlertLevels.length()").value(1));

        // 4. Final DEACTIVATE leaves empty activeAlertLevels -> ALL_CLEAR / GREEN
        String finalDeactivateJson = """
                {
                  "status": "DEACTIVATE",
                  "regionId": 14,
                  "alarmType": "AIR",
                  "createdAt": "2026-09-07T12:32:00Z",
                  "alertLevel": "Yellow",
                  "reason": "UAV activity",
                  "activeAlertLevels": []
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finalDeactivateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.dangerLevel").value("GREEN"))
                .andExpect(jsonPath("$.alertType").value("ALL_CLEAR"))
                .andExpect(jsonPath("$.activeAlertLevels.length()").value(0));

        assertFalse(alertStateService.hasActiveAlertForRegion("669"));

        List<Map<String, String>> dispatched = fcmPushService.getDispatchedPayloadsLog();
        assertEquals(4, dispatched.size());
        assertEquals("true", dispatched.get(1).get("isActive")); // partial deactivate did NOT send all-clear
        assertEquals("RED", dispatched.get(1).get("dangerLevel"));
        assertEquals("YELLOW", dispatched.get(2).get("dangerLevel"));
        assertEquals("ALL_CLEAR", dispatched.get(3).get("alertType"));
        assertEquals("false", dispatched.get(3).get("isActive"));
    }

    @Test
    void testInfoAlarmTypeIsCompletelyIgnoredAndMultipleAlarmTypesStayIsolated() throws Exception {
        // 1. INFO webhook should be ignored without updating state or dispatching FCM
        String infoJson = """
                {
                  "status": "Activate",
                  "regionId": 14,
                  "alarmType": "INFO",
                  "createdAt": "2026-09-07T12:00:00Z",
                  "alertLevel": "Yellow",
                  "reason": "Test message",
                  "activeAlertLevels": [
                    { "alertLevel": "Yellow", "reason": "Test message", "createdAt": "2026-09-07T12:00:00Z" }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(infoJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored"))
                .andExpect(jsonPath("$.dispatchedCount").value(0));

        assertEquals(0, fcmPushService.getDispatchedPayloadsLog().size());
        assertFalse(alertStateService.hasActiveAlertForRegion("14"));

        // 2. AIR active + ARTILLERY active -> deactivating ARTILLERY preserves active AIR
        String airActivate = """
                {
                  "status": "Activate",
                  "regionId": 14,
                  "alarmType": "AIR",
                  "createdAt": "2026-09-07T12:01:00Z",
                  "alertLevel": "Yellow",
                  "reason": "UAV activity",
                  "activeAlertLevels": [
                    { "alertLevel": "Yellow", "reason": "UAV activity", "createdAt": "2026-09-07T12:01:00Z" }
                  ]
                }
                """;
        String artilleryActivate = """
                {
                  "status": "Activate",
                  "regionId": 14,
                  "alarmType": "ARTILLERY",
                  "createdAt": "2026-09-07T12:02:00Z",
                  "alertLevel": "Red",
                  "reason": "Shelling risk",
                  "activeAlertLevels": [
                    { "alertLevel": "Red", "reason": "Shelling risk", "createdAt": "2026-09-07T12:02:00Z" }
                  ]
                }
                """;
        String artilleryClear = """
                {
                  "status": "DEACTIVATE",
                  "regionId": 14,
                  "alarmType": "ARTILLERY",
                  "createdAt": "2026-09-07T12:10:00Z",
                  "alertLevel": "Red",
                  "reason": "Shelling risk",
                  "activeAlertLevels": []
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm").contentType(MediaType.APPLICATION_JSON).content(airActivate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dangerLevel").value("YELLOW"));

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm").contentType(MediaType.APPLICATION_JSON).content(artilleryActivate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dangerLevel").value("RED"))
                .andExpect(jsonPath("$.activeAlertLevels.length()").value(2));

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm").contentType(MediaType.APPLICATION_JSON).content(artilleryClear))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(true))
                .andExpect(jsonPath("$.dangerLevel").value("YELLOW"))
                .andExpect(jsonPath("$.alertType").value("AIR"))
                .andExpect(jsonPath("$.activeAlertLevels.length()").value(1));
    }

    @Test
    void testPublicKeyResourceLoadingAndFailClosedWhenMissing() {
        if (new ClassPathResource("ukraine-alert-webhook-public.pem").exists()) {
            WebhookSignatureVerifier resourceVerifier = new WebhookSignatureVerifier(
                    true,
                    300,
                    WebhookSignatureVerifier.DEFAULT_PUBLIC_KEY_LOCATION,
                    "",
                    new DefaultResourceLoader(),
                    Clock.systemUTC()
            );
            assertTrue(resourceVerifier.isPublicKeyLoaded());
            assertEquals(2048, resourceVerifier.getPublicKey().getModulus().bitLength());
        }

        WebhookSignatureVerifier missingKeyVerifier = new WebhookSignatureVerifier(
                true,
                300,
                "classpath:missing-webhook-public.pem",
                "",
                new DefaultResourceLoader(),
                Clock.systemUTC()
        );
        assertFalse(missingKeyVerifier.isPublicKeyLoaded());
        assertFalse(missingKeyVerifier.verify("sig", "123", null, null, new byte[]{1}).valid());
    }

    @Test
    void testPingEndpointVerifiesBackendIdentityAndEchoesChallenge() {
        ResponseEntity<Map<String, Object>> resp = mobileApiController.ping("setup-nonce-123");
        assertNotNull(resp.getBody());
        assertEquals("air-alert-backend", resp.getBody().get("service"));
        assertEquals("ok", resp.getBody().get("status"));
        assertEquals(1, resp.getBody().get("apiVersion"));
        assertEquals("setup-nonce-123", resp.getBody().get("challenge"));
        assertTrue(((Integer) resp.getBody().get("regionsCount")) > 0);
    }

    @Test
    void testDefaultProfileIsDevelopmentAndSkipsWebhookRegistration() {
        assertTrue(environment.acceptsProfiles(Profiles.of(UkraineAlarmApiClient.PROFILE_DEVELOPMENT)));
        assertFalse(ukraineAlarmApiClient.isProduction());

        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setDefaultProfiles(UkraineAlarmApiClient.PROFILE_DEVELOPMENT);
        UkraineAlarmApiClient devClient = new UkraineAlarmApiClient(
                "https://api.ukrainealarm.com",
                "dummy-token",
                "https://alerts.example.com/api/v1/webhook/ukrainealarm",
                true,
                devEnv
        );
        assertFalse(devClient.isProduction());
        assertFalse(devClient.registerWebhook("https://alerts.example.com/api/v1/webhook/ukrainealarm"));
        assertFalse(devClient.updateWebhook("https://alerts.example.com/api/v1/webhook/ukrainealarm"));

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles(UkraineAlarmApiClient.PROFILE_PRODUCTION);
        UkraineAlarmApiClient prodClient = new UkraineAlarmApiClient(
                "https://api.ukrainealarm.com",
                "dummy-token",
                "https://alerts.example.com/api/v1/webhook/ukrainealarm",
                true,
                prodEnv
        );
        assertTrue(prodClient.isProduction());
    }
}
