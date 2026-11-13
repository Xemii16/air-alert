package ua.alert.backend.service;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final String credentialsPath;
    private volatile boolean firebaseInitialized = false;
    private final List<Map<String, String>> dispatchedPayloadsLog = new CopyOnWriteArrayList<>();

    public FcmPushService(@Value("${firebase.credentials-path:}") String credentialsPath) {
        this.credentialsPath = credentialsPath != null ? credentialsPath.trim() : "";
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

    public int sendAlertPush(
            String regionId,
            String regionName,
            String alertType,
            boolean isActive,
            Set<String> targetTokens
    ) {
        Map<String, String> data = new HashMap<>();
        data.put("regionId", regionId != null ? regionId : "31");
        data.put("regionName", regionName != null ? regionName : "Україна");
        data.put("alertType", isActive ? (alertType != null ? alertType : "AIR") : "ALL_CLEAR");
        data.put("isActive", String.valueOf(isActive));
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
            log.info("[FCM Dry-Run] Would send HIGH-priority FCM push to topic '{}' and {} tokens: {}",
                    topic, targetTokens != null ? targetTokens.size() : 0, data);
            return 1 + (targetTokens != null ? targetTokens.size() : 0);
        }

        // 1. Send to FCM topic (e.g., "region_31" or "region_14")
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

        // 2. Send directly to registered tokens for this region or its sub-regions
        if (targetTokens != null) {
            for (String token : targetTokens) {
                try {
                    Message tokenMessage = Message.builder()
                            .setToken(token)
                            .setAndroidConfig(androidConfig)
                            .putAllData(data)
                            .build();
                    FirebaseMessaging.getInstance().send(tokenMessage);
                    sentCount++;
                } catch (Exception e) {
                    log.warn("Failed to send FCM message to token {}: {}", token, e.getMessage());
                }
            }
        }

        return sentCount;
    }

    public List<Map<String, String>> getDispatchedPayloadsLog() {
        return List.copyOf(dispatchedPayloadsLog);
    }

    public void clearDispatchedPayloadsLog() {
        dispatchedPayloadsLog.clear();
    }
}
