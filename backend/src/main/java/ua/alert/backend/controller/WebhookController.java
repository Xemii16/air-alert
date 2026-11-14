package ua.alert.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.alert.backend.model.WebHookMessageModel;
import ua.alert.backend.service.AlertStateService;
import ua.alert.backend.service.AlertStateService.RegionAlertSnapshot;
import ua.alert.backend.service.FcmPushService;
import ua.alert.backend.service.RegionHierarchyService;
import ua.alert.backend.service.WebhookSignatureVerifier;

@RestController
@RequestMapping("/api/v1/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RegionHierarchyService hierarchyService;
    private final AlertStateService alertStateService;
    private final FcmPushService fcmPushService;
    private final WebhookSignatureVerifier signatureVerifier;
    private final ObjectMapper objectMapper;

    public WebhookController(
            RegionHierarchyService hierarchyService,
            AlertStateService alertStateService,
            FcmPushService fcmPushService,
            WebhookSignatureVerifier signatureVerifier,
            ObjectMapper objectMapper
    ) {
        this.hierarchyService = hierarchyService;
        this.alertStateService = alertStateService;
        this.fcmPushService = fcmPushService;
        this.signatureVerifier = signatureVerifier;
        this.objectMapper = objectMapper;
    }

    /**
     * Webhook callback endpoint registered with https://api.ukrainealarm.com (/api/v3/webhook).
     */
    @PostMapping("/ukrainealarm")
    public ResponseEntity<Map<String, Object>> onUkraineAlarmWebhook(
            @RequestHeader(value = WebhookSignatureVerifier.HEADER_SIGNATURE, required = false) String signature,
            @RequestHeader(value = WebhookSignatureVerifier.HEADER_TIMESTAMP, required = false) String timestamp,
            @RequestHeader(value = WebhookSignatureVerifier.HEADER_KEY_ID, required = false) String keyId,
            @RequestHeader(value = WebhookSignatureVerifier.HEADER_ALGORITHM, required = false) String algorithm,
            @RequestBody(required = false) byte[] rawBodyBytes
    ) {
        WebhookSignatureVerifier.VerificationResult verification =
                signatureVerifier.verify(signature, timestamp, keyId, algorithm, rawBodyBytes);
        if (!verification.valid()) {
            log.warn("Rejected UkraineAlarm webhook request: {}", verification.reason());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", verification.reason()));
        }

        final WebHookMessageModel payload;
        try {
            payload = objectMapper.readValue(rawBodyBytes, WebHookMessageModel.class);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid JSON webhook payload"));
        }

        return processValidatedPayload(payload);
    }

    public ResponseEntity<Map<String, Object>> processValidatedPayload(WebHookMessageModel payload) {
        if (payload == null || payload.getRegionId() == null || payload.getRegionId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing regionId in webhook payload"));
        }

        String regionId = payload.getRegionId().trim();
        String alarmType = AlertStateService.normalizeAlarmType(payload.getAlarmType());

        if (AlertStateService.isIgnoredAlarmType(alarmType)) {
            log.info("Ignoring INFO webhook for regionId={}", regionId);
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "INFO alarmType skipped",
                    "regionId", regionId,
                    "dispatchedCount", 0
            ));
        }

        String regionName = hierarchyService.getRegionName(regionId, null);
        RegionAlertSnapshot snapshot = alertStateService.applyWebhookSnapshot(payload, regionName);
        if (snapshot == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid webhook snapshot"));
        }

        log.info(
                "Processed UkraineAlarm webhook: regionId={}, regionName='{}', eventStatus={}, eventAlarmType={}, "
                        + "eventLevel={}, eventReason='{}', snapshotActive={}, dangerLevel={}, activeCount={}",
                regionId,
                regionName,
                payload.getStatus(),
                alarmType,
                payload.getAlertLevel(),
                payload.getReason(),
                snapshot.isActive(),
                snapshot.dangerLevel(),
                snapshot.activeAlertLevels().size()
        );

        int dispatched = fcmPushService.sendAlertPush(snapshot, payload);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "processed");
        body.put("regionId", snapshot.regionId());
        body.put("regionName", snapshot.regionName());
        body.put("isActive", snapshot.isActive());
        body.put("alertType", snapshot.primaryAlarmType());
        body.put("dangerLevel", snapshot.dangerLevel());
        body.put("eventStatus", payload.getStatus() != null ? payload.getStatus() : "");
        body.put("activeAlertLevels", snapshot.activeAlertLevels());
        body.put("dispatchedCount", dispatched);

        return ResponseEntity.ok(body);
    }
}
