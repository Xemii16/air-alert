package ua.alert.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ua.alert.backend.client.UkraineAlarmApiClient;
import ua.alert.backend.service.FcmPushService;
import ua.alert.backend.service.WebhookSignatureVerifier;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(UkraineAlarmApiClient.PROFILE_PRODUCTION)
class ProductionWebhookSignatureTest {

    private static final KeyPair TEST_KEY_PAIR = generateRsaKeyPair();

    @DynamicPropertySource
    static void registerWebhookTestKey(DynamicPropertyRegistry registry) {
        String base64Pub = Base64.getEncoder().encodeToString(TEST_KEY_PAIR.getPublic().getEncoded());
        String pem = "-----BEGIN PUBLIC KEY-----\n" + base64Pub + "\n-----END PUBLIC KEY-----";
        registry.add("ukrainealarm.webhook.signature.public-key-pem", () -> pem);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FcmPushService fcmPushService;

    @Autowired
    private UkraineAlarmApiClient ukraineAlarmApiClient;

    @BeforeEach
    void setUp() {
        fcmPushService.clearDispatchedPayloadsLog();
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String signPayload(PrivateKey privateKey, String timestamp, String rawBody) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(privateKey);
            signer.update((timestamp + "." + rawBody).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void testProductionProfileEnforcesSignatureValidation() throws Exception {
        assertTrue(ukraineAlarmApiClient.isProduction());

        String rawJson = "{\"status\":\"DEACTIVATE\",\"regionId\":31,\"alarmType\":\"AIR\","
                + "\"createdAt\":\"2026-09-07T12:32:00Z\",\"alertLevel\":\"Yellow\","
                + "\"reason\":\"UAV activity\",\"activeAlertLevels\":[]}";
        String nowTs = String.valueOf(Instant.now().getEpochSecond());
        String validSig = signPayload(TEST_KEY_PAIR.getPrivate(), nowTs, rawJson);

        // 1. Missing headers
        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawJson))
                .andExpect(status().isUnauthorized());

        // 2. Unsupported algorithm header
        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureVerifier.HEADER_SIGNATURE, validSig)
                        .header(WebhookSignatureVerifier.HEADER_TIMESTAMP, nowTs)
                        .header(WebhookSignatureVerifier.HEADER_ALGORITHM, "hmac-sha256")
                        .content(rawJson))
                .andExpect(status().isUnauthorized());

        // 3. Tampered raw body (extra space breaks raw-body signature)
        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureVerifier.HEADER_SIGNATURE, validSig)
                        .header(WebhookSignatureVerifier.HEADER_TIMESTAMP, nowTs)
                        .header(WebhookSignatureVerifier.HEADER_ALGORITHM, "rsa-sha256")
                        .content(rawJson + " "))
                .andExpect(status().isUnauthorized());

        // 4. Forged signature from untrusted private key
        KeyPair attackerKey = generateRsaKeyPair();
        String forgedSig = signPayload(attackerKey.getPrivate(), nowTs, rawJson);
        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureVerifier.HEADER_SIGNATURE, forgedSig)
                        .header(WebhookSignatureVerifier.HEADER_TIMESTAMP, nowTs)
                        .content(rawJson))
                .andExpect(status().isUnauthorized());

        // 5. Expired timestamp (> 5 minutes old)
        String expiredTs = String.valueOf(Instant.now().getEpochSecond() - 360);
        String expiredSig = signPayload(TEST_KEY_PAIR.getPrivate(), expiredTs, rawJson);
        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureVerifier.HEADER_SIGNATURE, expiredSig)
                        .header(WebhookSignatureVerifier.HEADER_TIMESTAMP, expiredTs)
                        .content(rawJson))
                .andExpect(status().isUnauthorized());

        assertEquals(0, fcmPushService.getDispatchedPayloadsLog().size());

        // 6. Valid signed request succeeds once, exact replay is rejected
        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureVerifier.HEADER_SIGNATURE, validSig)
                        .header(WebhookSignatureVerifier.HEADER_TIMESTAMP, nowTs)
                        .header(WebhookSignatureVerifier.HEADER_KEY_ID, "test-key-1")
                        .header(WebhookSignatureVerifier.HEADER_ALGORITHM, "rsa-sha256")
                        .content(rawJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.alertType").value("ALL_CLEAR"));

        mockMvc.perform(post("/api/v1/webhook/ukrainealarm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(WebhookSignatureVerifier.HEADER_SIGNATURE, validSig)
                        .header(WebhookSignatureVerifier.HEADER_TIMESTAMP, nowTs)
                        .header(WebhookSignatureVerifier.HEADER_ALGORITHM, "rsa-sha256")
                        .content(rawJson))
                .andExpect(status().isUnauthorized());

        assertEquals(1, fcmPushService.getDispatchedPayloadsLog().size());
    }

    @Test
    void testParseSpaceSeparatedPemForSystemdEnvironmentFile() {
        String base64Pub = Base64.getEncoder().encodeToString(TEST_KEY_PAIR.getPublic().getEncoded());
        String chunked = base64Pub.replaceAll("(.{64})", "$1 ");
        String spaceSeparatedPem = "-----BEGIN PUBLIC KEY----- " + chunked.trim() + " -----END PUBLIC KEY-----";

        assertEquals(
                TEST_KEY_PAIR.getPublic(),
                WebhookSignatureVerifier.parsePublicKeyPem(spaceSeparatedPem)
        );
    }
}
