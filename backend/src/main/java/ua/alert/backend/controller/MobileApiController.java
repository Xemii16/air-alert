package ua.alert.backend.controller;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.alert.backend.client.UkraineAlarmApiClient;
import ua.alert.backend.model.AlertDto;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.model.DeviceRegistrationRequest;
import ua.alert.backend.model.RegionsViewModel;
import ua.alert.backend.model.TestWebhookRequest;
import ua.alert.backend.model.WebHookModel;
import ua.alert.backend.service.DeviceSubscriptionService;
import ua.alert.backend.service.RegionHierarchyService;

@RestController
@RequestMapping("/api/v1")
public class MobileApiController {

    private final RegionHierarchyService hierarchyService;
    private final DeviceSubscriptionService subscriptionService;
    private final UkraineAlarmApiClient apiClient;
    private final WebhookController webhookController;

    public MobileApiController(
            RegionHierarchyService hierarchyService,
            DeviceSubscriptionService subscriptionService,
            UkraineAlarmApiClient apiClient,
            WebhookController webhookController
    ) {
        this.hierarchyService = hierarchyService;
        this.subscriptionService = subscriptionService;
        this.apiClient = apiClient;
        this.webhookController = webhookController;
    }

    @GetMapping("/regions")
    public ResponseEntity<RegionsViewModel> getRegions() {
        return ResponseEntity.ok(hierarchyService.getRegionsTree());
    }

    @GetMapping("/alerts/{regionId}")
    public ResponseEntity<Map<String, Object>> getAlertStatus(@PathVariable String regionId) {
        AlertRegionModel active = subscriptionService.getActiveAlertForRegion(regionId);
        if (active == null && apiClient.isConfigured()) {
            List<AlertRegionModel> live = apiClient.fetchAlertsForRegion(regionId);
            if (!live.isEmpty() && live.get(0).getActiveAlerts() != null && !live.get(0).getActiveAlerts().isEmpty()) {
                active = live.get(0);
                subscriptionService.updateAlertState(active, true);
            }
        }

        boolean isActive = active != null && active.getActiveAlerts() != null && !active.getActiveAlerts().isEmpty();
        String alertType = isActive ? active.getActiveAlerts().get(0).getAlertType() : "ALL_CLEAR";
        String regionName = hierarchyService.getRegionName(regionId, active != null ? active.getRegionName() : null);

        return ResponseEntity.ok(Map.of(
                "regionId", regionId,
                "regionName", regionName,
                "isActive", isActive,
                "alertType", alertType != null ? alertType : "AIR"
        ));
    }

    @PostMapping("/devices/register")
    public ResponseEntity<Map<String, Object>> registerDevice(@Valid @RequestBody DeviceRegistrationRequest request) {
        subscriptionService.registerDevice(request);
        return ResponseEntity.ok(Map.of(
                "status", "registered",
                "deviceId", request.getDeviceId(),
                "effectiveRegionId", request.getEffectiveRegionId(),
                "subscribedTopics", hierarchyService.getLineageIds(request.getEffectiveRegionId())
        ));
    }

    @PostMapping("/webhook/register")
    public ResponseEntity<Map<String, Object>> registerRemoteWebhook(@RequestBody WebHookModel model) {
        boolean ok = apiClient.registerWebhook(model.getWebHookUrl());
        return ResponseEntity.ok(Map.of("registered", ok, "webHookUrl", model.getWebHookUrl()));
    }

    @DeleteMapping("/webhook")
    public ResponseEntity<Map<String, Object>> deleteRemoteWebhook(@RequestBody WebHookModel model) {
        boolean ok = apiClient.deleteWebhook(model.getWebHookUrl());
        return ResponseEntity.ok(Map.of("deleted", ok, "webHookUrl", model.getWebHookUrl()));
    }

    @PostMapping("/test/webhook")
    public ResponseEntity<Map<String, Object>> triggerTestWebhook(@RequestBody TestWebhookRequest testReq) {
        AlertRegionModel simulated = new AlertRegionModel();
        simulated.setRegionId(testReq.getRegionId());
        simulated.setRegionName(testReq.getRegionName());
        simulated.setLastUpdate(Instant.now().toString());

        if (testReq.isActive() && !"ALL_CLEAR".equalsIgnoreCase(testReq.getAlertType())) {
            AlertDto alertDto = new AlertDto(
                    testReq.getRegionId(),
                    testReq.getAlertType(),
                    Instant.now().toString(),
                    null
            );
            simulated.setActiveAlerts(List.of(alertDto));
        } else {
            simulated.setActiveAlerts(Collections.emptyList());
        }

        return webhookController.onUkraineAlarmWebhook(simulated);
    }
}
