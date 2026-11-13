package ua.alert.backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.alert.backend.model.AlertDto;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.service.DeviceSubscriptionService;
import ua.alert.backend.service.FcmPushService;
import ua.alert.backend.service.RegionHierarchyService;

@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RegionHierarchyService hierarchyService;
    private final DeviceSubscriptionService subscriptionService;
    private final FcmPushService fcmPushService;

    public WebhookController(
            RegionHierarchyService hierarchyService,
            DeviceSubscriptionService subscriptionService,
            FcmPushService fcmPushService
    ) {
        this.hierarchyService = hierarchyService;
        this.subscriptionService = subscriptionService;
        this.fcmPushService = fcmPushService;
    }

    /**
     * Webhook callback endpoint registered with https://api.ukrainealarm.com (/api/v3/webhook).
     */
    @PostMapping("/ukrainealarm")
    public ResponseEntity<Map<String, Object>> onUkraineAlarmWebhook(@RequestBody AlertRegionModel payload) {
        if (payload == null || payload.getRegionId() == null || payload.getRegionId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing regionId in webhook payload"));
        }

        String regionId = payload.getRegionId();
        String regionName = hierarchyService.getRegionName(regionId, payload.getRegionName());
        payload.setRegionName(regionName);

        boolean isActive;
        String alertType = "AIR";

        List<AlertDto> activeAlerts = payload.getActiveAlerts();
        if (activeAlerts != null && !activeAlerts.isEmpty()) {
            AlertDto primary = activeAlerts.get(0);
            isActive = primary.getEndDate() == null || primary.getEndDate().isBlank();
            if (primary.getAlertType() != null && !primary.getAlertType().isBlank()) {
                alertType = primary.getAlertType();
            }
        } else if (payload.getStatus() != null) {
            isActive = "Activate".equalsIgnoreCase(payload.getStatus())
                    || "Active".equalsIgnoreCase(payload.getStatus());
            if (payload.getAlertType() != null && !payload.getAlertType().isBlank()) {
                alertType = payload.getAlertType();
            }
        } else {
            isActive = false;
            alertType = "ALL_CLEAR";
        }

        if (!isActive) {
            alertType = "ALL_CLEAR";
        }

        log.info("Received UkraineAlarm webhook: regionId={}, regionName='{}', isActive={}, alertType={}",
                regionId, regionName, isActive, alertType);

        subscriptionService.updateAlertState(payload, isActive);
        Set<String> targetTokens = subscriptionService.findMatchingFcmTokens(regionId);

        int dispatched = fcmPushService.sendAlertPush(regionId, regionName, alertType, isActive, targetTokens);

        return ResponseEntity.ok(Map.of(
                "status", "processed",
                "regionId", regionId,
                "regionName", regionName,
                "isActive", isActive,
                "alertType", alertType,
                "dispatchedCount", dispatched
        ));
    }
}
