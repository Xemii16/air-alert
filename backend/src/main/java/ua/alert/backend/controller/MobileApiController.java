package ua.alert.backend.controller;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import ua.alert.backend.model.ActiveAlertLevelDto;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.model.RegionsViewModel;
import ua.alert.backend.model.TestWebhookRequest;
import ua.alert.backend.model.WebHookMessageModel;
import ua.alert.backend.model.WebHookModel;
import ua.alert.backend.service.AlertStateService;
import ua.alert.backend.service.AlertStateService.RegionAlertSnapshot;
import ua.alert.backend.service.RegionHierarchyService;

@RestController
@RequestMapping("/api/v1")
public class MobileApiController {

    private final RegionHierarchyService hierarchyService;
    private final AlertStateService alertStateService;
    private final UkraineAlarmApiClient apiClient;
    private final WebhookController webhookController;

    public MobileApiController(
            RegionHierarchyService hierarchyService,
            AlertStateService alertStateService,
            UkraineAlarmApiClient apiClient,
            WebhookController webhookController
    ) {
        this.hierarchyService = hierarchyService;
        this.alertStateService = alertStateService;
        this.apiClient = apiClient;
        this.webhookController = webhookController;
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(
            @org.springframework.web.bind.annotation.RequestParam(name = "challenge", required = false) String challenge
    ) {
        RegionsViewModel regions = hierarchyService.getRegionsTree();
        int statesCount = (regions != null && regions.getStates() != null) ? regions.getStates().size() : 0;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("service", "air-alert-backend");
        response.put("status", "ok");
        response.put("apiVersion", 1);
        response.put("ukraineAlarmConfigured", apiClient.isConfigured());
        response.put("regionsCount", statesCount);
        response.put("timestamp", Instant.now().toString());
        if (challenge != null && !challenge.isBlank()) {
            response.put("challenge", challenge.trim());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/regions")
    public ResponseEntity<RegionsViewModel> getRegions(
            @org.springframework.web.bind.annotation.RequestParam(name = "refresh", defaultValue = "false") boolean refresh
    ) {
        if (refresh && apiClient.isConfigured()) {
            hierarchyService.refreshHierarchy();
        }
        return ResponseEntity.ok(hierarchyService.getRegionsTree());
    }

    @GetMapping("/alerts/{regionId}")
    public ResponseEntity<Map<String, Object>> getAlertStatus(@PathVariable String regionId) {
        String regionName = hierarchyService.getRegionName(regionId, null);
        RegionAlertSnapshot snapshot = alertStateService.getAggregatedSnapshotForLineage(regionId, regionName);

        if (!snapshot.isActive() && apiClient.isConfigured()) {
            List<AlertRegionModel> live = apiClient.fetchAlertsForRegion(regionId);
            if (!live.isEmpty()) {
                alertStateService.applyRestApiAlerts(regionId, live);
                snapshot = alertStateService.getAggregatedSnapshotForLineage(regionId, regionName);
            }
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("regionId", snapshot.regionId());
        body.put("regionName", snapshot.regionName());
        body.put("isActive", snapshot.isActive());
        body.put("alertType", snapshot.primaryAlarmType());
        body.put("alarmType", snapshot.primaryAlarmType());
        body.put("dangerLevel", snapshot.dangerLevel());
        body.put("activeAlertLevels", snapshot.activeAlertLevels());
        return ResponseEntity.ok(body);
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
        String now = (testReq.getCreatedAt() != null && !testReq.getCreatedAt().isBlank())
                ? testReq.getCreatedAt()
                : Instant.now().toString();
        String alarmType = (testReq.getAlarmType() != null && !testReq.getAlarmType().isBlank())
                ? testReq.getAlarmType()
                : testReq.getAlertType();
        String status = (testReq.getStatus() != null && !testReq.getStatus().isBlank())
                ? testReq.getStatus()
                : (testReq.isActive() && !"ALL_CLEAR".equalsIgnoreCase(alarmType) ? "Activate" : "DEACTIVATE");
        String alertLevel = ActiveAlertLevelDto.normalizeAlertLevel(testReq.getAlertLevel());
        String reason = testReq.getReason() != null ? testReq.getReason() : "";

        WebHookMessageModel simulated = new WebHookMessageModel();
        simulated.setRegionId(testReq.getRegionId());
        simulated.setStatus(status);
        simulated.setAlarmType("ALL_CLEAR".equalsIgnoreCase(alarmType) ? "AIR" : alarmType);
        simulated.setCreatedAt(now);
        simulated.setAlertLevel(alertLevel);
        simulated.setReason(reason);

        if (testReq.getActiveAlertLevels() != null) {
            simulated.setActiveAlertLevels(testReq.getActiveAlertLevels());
        } else if ("Activate".equalsIgnoreCase(status) && !"ALL_CLEAR".equalsIgnoreCase(alarmType)) {
            simulated.setActiveAlertLevels(List.of(new ActiveAlertLevelDto(alertLevel, reason, now)));
        } else {
            simulated.setActiveAlertLevels(Collections.emptyList());
        }

        return webhookController.processValidatedPayload(simulated);
    }
}
