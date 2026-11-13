package ua.alert.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import ua.alert.backend.controller.MobileApiController;
import ua.alert.backend.controller.WebhookController;
import ua.alert.backend.model.AlertDto;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.model.DeviceRegistrationRequest;
import ua.alert.backend.model.RegionsViewModel;
import ua.alert.backend.service.DeviceSubscriptionService;
import ua.alert.backend.service.FcmPushService;

@SpringBootTest
class WebhookControllerTest {

    @Autowired
    private WebhookController webhookController;

    @Autowired
    private MobileApiController mobileApiController;

    @Autowired
    private DeviceSubscriptionService subscriptionService;

    @Autowired
    private FcmPushService fcmPushService;

    @BeforeEach
    void setUp() {
        fcmPushService.clearDispatchedPayloadsLog();
    }

    @Test
    void testRegionsTreeEndpointReturnsHierarchy() {
        ResponseEntity<RegionsViewModel> response = mobileApiController.getRegions();
        assertNotNull(response.getBody());
        assertFalse(response.getBody().getStates().isEmpty());
    }

    @Test
    void testOblastWebhookMatchesSubscribedChildCommunityAndDispatchesFcm() {
        // Register a device subscribed to Irpin Community (669) inside Bucha District (64) inside Kyiv Oblast (14)
        DeviceRegistrationRequest reg = new DeviceRegistrationRequest();
        reg.setDeviceId("watch-companion-1");
        reg.setFcmToken("test-fcm-token-irpin");
        reg.setStateId("14");
        reg.setDistrictId("64");
        reg.setCommunityId("669");
        reg.setRegionName("Ірпінська територіальна громада");
        mobileApiController.registerDevice(reg);

        // Verify hierarchy matching when an Oblast-wide alert arrives for Kyiv Oblast (14)
        Set<String> matchedTokens = subscriptionService.findMatchingFcmTokens("14");
        assertTrue(matchedTokens.contains("test-fcm-token-irpin"));

        // Simulate UkraineAlarm v3 webhook for Kyiv Oblast (14)
        AlertRegionModel webhookPayload = new AlertRegionModel();
        webhookPayload.setRegionId("14");
        webhookPayload.setRegionName("Київська область");
        webhookPayload.setRegionType("State");
        webhookPayload.setActiveAlerts(List.of(
                new AlertDto("14", "AIR", "2026-11-09T12:00:00Z", null)
        ));

        ResponseEntity<Map<String, Object>> resp = webhookController.onUkraineAlarmWebhook(webhookPayload);
        assertNotNull(resp.getBody());
        assertEquals(true, resp.getBody().get("isActive"));
        assertEquals("AIR", resp.getBody().get("alertType"));

        List<Map<String, String>> dispatched = fcmPushService.getDispatchedPayloadsLog();
        assertEquals(1, dispatched.size());
        assertEquals("14", dispatched.get(0).get("regionId"));
        assertEquals("AIR", dispatched.get(0).get("alertType"));
        assertEquals("true", dispatched.get(0).get("isActive"));
    }

    @Test
    void testAllClearWebhookWhenActiveAlertsEmpty() {
        AlertRegionModel allClearPayload = new AlertRegionModel();
        allClearPayload.setRegionId("31");
        allClearPayload.setRegionName("м. Київ");
        allClearPayload.setActiveAlerts(Collections.emptyList());

        ResponseEntity<Map<String, Object>> resp = webhookController.onUkraineAlarmWebhook(allClearPayload);
        assertNotNull(resp.getBody());
        assertEquals(false, resp.getBody().get("isActive"));
        assertEquals("ALL_CLEAR", resp.getBody().get("alertType"));

        List<Map<String, String>> dispatched = fcmPushService.getDispatchedPayloadsLog();
        assertEquals(1, dispatched.size());
        assertEquals("ALL_CLEAR", dispatched.get(0).get("alertType"));
        assertEquals("false", dispatched.get(0).get("isActive"));
    }
}
