package ua.alert.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ua.alert.backend.model.ActiveAlertLevelDto;
import ua.alert.backend.model.WebHookMessageModel;
import ua.alert.backend.service.AlertStateService.RegionAlertSnapshot;

@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);
    private static final int MAX_LEVELS_JSON_BYTES = 2800;

    private final String credentialsPath;
    private final ObjectMapper objectMapper;
    private volatile boolean firebaseInitialized = false;
    private final List<Map<String, String>> dispatchedPayloadsLog = new CopyOnWriteArrayList<>();

    public FcmPushService(String credentialsPath) {
        this(credentialsPath, new ObjectMapper());
    }

    @Autowired
    public FcmPushService(
            @Value("${firebase.credentials-path:}") String credentialsPath,
            ObjectMapper objectMapper
    ) {
        this.credentialsPath = credentialsPath != null ? credentialsPath.trim() : "";
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void initFirebase() {
        try {
            if (!FirebaseApp.getApps().isEmpty()) {
                firebaseInitialized = true;
                return;
            }
            if (!credentialsPath.isEmpty()) {
                File credFile = new File(credentialsPath);
                if (credFile.exists()) {
                    try (InputStream serviceAccount = new FileInputStream(credFile)) {
                        FirebaseOptions options = FirebaseOptions.builder()
                                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                                .build();
                        FirebaseApp.initializeApp(options);
                        firebaseInitialized = true;
                        log.info("Firebase Admin SDK initialized from credentials file: {}", credentialsPath);
                        return;
                    }
                }
            }
            String defaultEnv = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (defaultEnv != null && !defaultEnv.isBlank() && new File(defaultEnv).exists()) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.getApplicationDefault())
                        .build();
                FirebaseApp.initializeApp(options);
                firebaseInitialized = true;
                log.info("Firebase Admin SDK initialized from GOOGLE_APPLICATION_CREDENTIALS");
            } else {
                log.info("Firebase credentials not configured yet (set FIREBASE_CREDENTIALS_PATH). Running in dry-run log mode.");
            }
        } catch (Exception e) {
            log.warn("Could not initialize Firebase Admin SDK: {}. Running in dry-run log mode.", e.getMessage());
        }
    }

    public int sendAlertPush(RegionAlertSnapshot snapshot, WebHookMessageModel event) {
        boolean isActive = snapshot.isActive();
        String eventAlarmType = AlertStateService.normalizeAlarmType(event != null ? event.getAlarmType() : null);

        Map<String, String> data = new HashMap<>();
        data.put("regionId", snapshot.regionId() != null ? snapshot.regionId() : "31");
        data.put("regionName", snapshot.regionName() != null ? snapshot.regionName() : "Україна");
        data.put("isActive", String.valueOf(isActive));
        data.put("alertType", isActive ? snapshot.primaryAlarmType() : AlertStateService.ALARM_TYPE_ALL_CLEAR);
        data.put("alarmType", snapshot.primaryAlarmType());
        data.put("dangerLevel", snapshot.dangerLevel());
        data.put("eventStatus", event != null && event.getStatus() != null ? event.getStatus() : "");
        data.put("eventAlarmType", eventAlarmType);
        data.put("eventAlertLevel", event != null && event.getAlertLevel() != null
                ? ActiveAlertLevelDto.normalizeAlertLevel(event.getAlertLevel())
                : "");
        data.put("eventReason", event != null && event.getReason() != null ? event.getReason() : "");
        data.put("createdAt", event != null && event.getCreatedAt() != null ? event.getCreatedAt() : "");
        data.put("activeAlertLevels", serializeActiveLevelsCapped(snapshot.activeAlertLevels()));
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        dispatchedPayloadsLog.add(Map.copyOf(data));
        if (dispatchedPayloadsLog.size() > 100) {
            dispatchedPayloadsLog.remove(0);
        }

        AndroidConfig androidConfig = AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setTtl(300_000L) // 5 minutes TTL for urgent delivery
                .build();

        String topic = "region_" + data.get("regionId");
        int sentCount = 0;

        if (!firebaseInitialized) {
            log.info("[FCM Dry-Run] Would send HIGH-priority FCM push to topic '{}': {}", topic, data);
            return 1;
        }

        try {
            Message topicMessage = Message.builder()
                    .setTopic(topic)
                    .setAndroidConfig(androidConfig)
                    .putAllData(data)
                    .build();
            String msgId = FirebaseMessaging.getInstance().send(topicMessage);
            log.info("Sent FCM alert push to topic '{}': messageId={}", topic, msgId);
            sentCount++;
        } catch (Exception e) {
            log.error("Failed to send FCM message to topic {}", topic, e);
        }

        return sentCount;
    }

    private String serializeActiveLevelsCapped(List<ActiveAlertLevelDto> levels) {
        if (levels == null || levels.isEmpty()) {
            return "[]";
        }
        try {
            String json = objectMapper.writeValueAsString(levels);
            if (json.getBytes(StandardCharsets.UTF_8).length <= MAX_LEVELS_JSON_BYTES) {
                return json;
            }
            // Prioritize keeping Red levels first if list is unusually large for FCM's 4 KB cap
            List<ActiveAlertLevelDto> sorted = new java.util.ArrayList<>(levels);
            sorted.sort((a, b) -> Boolean.compare(b.isRed(), a.isRed()));
            while (!sorted.isEmpty()) {
                String candidate = objectMapper.writeValueAsString(sorted);
                if (candidate.getBytes(StandardCharsets.UTF_8).length <= MAX_LEVELS_JSON_BYTES) {
                    return candidate;
                }
                sorted.remove(sorted.size() - 1);
            }
        } catch (Exception e) {
            log.warn("Failed to serialize activeAlertLevels JSON: {}", e.getMessage());
        }
        return "[]";
    }

    public List<Map<String, String>> getDispatchedPayloadsLog() {
        return List.copyOf(dispatchedPayloadsLog);
    }

    public void clearDispatchedPayloadsLog() {
        dispatchedPayloadsLog.clear();
    }
}
