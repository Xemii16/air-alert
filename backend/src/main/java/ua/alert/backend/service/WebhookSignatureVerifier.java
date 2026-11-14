package ua.alert.backend.service;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * Verifies UkraineAlarm webhook requests per https://api.ukrainealarm.com/webhook-signature-validation.html:
 * RSA-SHA256 (PKCS#1 v1.5) over canonical string "{X-Webhook-Timestamp}.{raw_request_body}" (UTF-8),
 * plus 5-minute timestamp window and replay deduplication.
 */
@Service
public class WebhookSignatureVerifier {

    public static final String HEADER_SIGNATURE = "X-Webhook-Signature";
    public static final String HEADER_TIMESTAMP = "X-Webhook-Timestamp";
    public static final String HEADER_KEY_ID = "X-Webhook-Key-Id";
    public static final String HEADER_ALGORITHM = "X-Webhook-Signature-Alg";
    public static final String EXPECTED_ALGORITHM = "rsa-sha256";
    public static final String DEFAULT_PUBLIC_KEY_LOCATION = "classpath:ukraine-alert-webhook-public.pem";

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);

    private final boolean enabled;
    private final long maxAgeSeconds;
    private final RSAPublicKey publicKey;
    private final Clock clock;
    private final ConcurrentHashMap<String, Long> seenRequestHashes = new ConcurrentHashMap<>();

    @Autowired
    public WebhookSignatureVerifier(
            @Value("${ukrainealarm.webhook.signature.enabled:true}") boolean enabled,
            @Value("${ukrainealarm.webhook.signature.max-age-seconds:300}") long maxAgeSeconds,
            @Value("${ukrainealarm.webhook.signature.public-key-location:" + DEFAULT_PUBLIC_KEY_LOCATION + "}") String publicKeyLocation,
            @Value("${ukrainealarm.webhook.signature.public-key-pem:}") String configuredPublicKeyPem,
            ResourceLoader resourceLoader
    ) {
        this(enabled, maxAgeSeconds, publicKeyLocation, configuredPublicKeyPem, resourceLoader, Clock.systemUTC());
    }

    public WebhookSignatureVerifier(
            boolean enabled,
            long maxAgeSeconds,
            String publicKeyLocation,
            String configuredPublicKeyPem,
            ResourceLoader resourceLoader,
            Clock clock
    ) {
        this.enabled = enabled;
        this.maxAgeSeconds = maxAgeSeconds > 0 ? maxAgeSeconds : 300L;
        this.clock = clock;
        if (!enabled) {
            this.publicKey = null;
            log.info("Webhook signature verification is disabled");
        } else {
            this.publicKey = resolvePublicKey(
                    publicKeyLocation,
                    configuredPublicKeyPem,
                    resourceLoader != null ? resourceLoader : new DefaultResourceLoader()
            );
        }
    }

    private static RSAPublicKey resolvePublicKey(
            String publicKeyLocation,
            String configuredPublicKeyPem,
            ResourceLoader resourceLoader
    ) {
        if (configuredPublicKeyPem != null && !configuredPublicKeyPem.isBlank()) {
            RSAPublicKey loaded = parsePublicKeyPem(configuredPublicKeyPem);
            log.info("Loaded UkraineAlarm webhook RSA public key from WEBHOOK_PUBLIC_KEY_PEM");
            return loaded;
        }
        if (publicKeyLocation != null && !publicKeyLocation.isBlank()) {
            Resource resource = resourceLoader.getResource(publicKeyLocation.trim());
            if (resource.exists()) {
                try {
                    String pem = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    RSAPublicKey loaded = parsePublicKeyPem(pem);
                    log.info("Loaded UkraineAlarm webhook RSA public key from {}", publicKeyLocation.trim());
                    return loaded;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to read webhook RSA public key from " + publicKeyLocation, e);
                }
            }
            log.warn("Webhook RSA public key resource not found at {}; incoming signed webhooks will be rejected until configured.",
                    publicKeyLocation.trim());
        }
        return null;
    }

    public static RSAPublicKey parsePublicKeyPem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("Webhook RSA public key PEM must not be blank");
        }
        String normalized = pem.replace("\\n", "\n")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\"", "")
                .replace("'", "")
                .replaceAll("\\s+", "");
        try {
            byte[] derBytes = Base64.getDecoder().decode(normalized);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derBytes);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid webhook RSA public key PEM", e);
        }
    }

    public boolean isPublicKeyLoaded() {
        return publicKey != null;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public VerificationResult verify(
            String signatureHeader,
            String timestampHeader,
            String keyIdHeader,
            String algorithmHeader,
            byte[] rawBodyBytes
    ) {
        if (!enabled) {
            return VerificationResult.ok();
        }
        if (publicKey == null) {
            return VerificationResult.rejected("Webhook RSA public key is not configured");
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return VerificationResult.rejected("Missing " + HEADER_SIGNATURE + " header");
        }
        if (timestampHeader == null || timestampHeader.isBlank()) {
            return VerificationResult.rejected("Missing " + HEADER_TIMESTAMP + " header");
        }
        if (algorithmHeader != null && !algorithmHeader.isBlank()
                && !EXPECTED_ALGORITHM.equalsIgnoreCase(algorithmHeader.trim())) {
            return VerificationResult.rejected("Unsupported " + HEADER_ALGORITHM + ": " + algorithmHeader.trim());
        }

        final long timestampSeconds;
        try {
            timestampSeconds = Long.parseLong(timestampHeader.trim());
        } catch (NumberFormatException e) {
            return VerificationResult.rejected("Invalid " + HEADER_TIMESTAMP + " value");
        }

        long nowSeconds = clock.instant().getEpochSecond();
        long ageSeconds = nowSeconds - timestampSeconds;
        if (ageSeconds > maxAgeSeconds) {
            return VerificationResult.rejected("Expired " + HEADER_TIMESTAMP);
        }
        if (timestampSeconds - nowSeconds > maxAgeSeconds) {
            return VerificationResult.rejected(HEADER_TIMESTAMP + " is too far in the future");
        }

        if (rawBodyBytes == null || rawBodyBytes.length == 0) {
            return VerificationResult.rejected("Empty webhook request body");
        }

        final byte[] signatureBytes;
        try {
            signatureBytes = Base64.getDecoder().decode(signatureHeader.trim());
        } catch (IllegalArgumentException e) {
            return VerificationResult.rejected("Invalid Base64 in " + HEADER_SIGNATURE);
        }

        byte[] canonicalBytes = buildCanonicalPayload(timestampHeader.trim(), rawBodyBytes);
        boolean verified;
        try {
            Signature rsaVerifier = Signature.getInstance("SHA256withRSA");
            rsaVerifier.initVerify(publicKey);
            rsaVerifier.update(canonicalBytes);
            verified = rsaVerifier.verify(signatureBytes);
        } catch (Exception e) {
            log.warn("RSA signature verification error (keyId={}): {}", keyIdHeader, e.getMessage());
            return VerificationResult.rejected("Webhook signature verification failed");
        }

        if (!verified) {
            log.warn("Invalid webhook signature rejected (keyId={})", keyIdHeader);
            return VerificationResult.rejected("Invalid webhook signature");
        }

        pruneExpiredHashes(nowSeconds);
        String replayKey = sha256Hex(canonicalBytes, signatureBytes);
        long expiresAt = nowSeconds + maxAgeSeconds;
        if (seenRequestHashes.putIfAbsent(replayKey, expiresAt) != null) {
            log.warn("Replayed webhook rejected (keyId={}, timestamp={})", keyIdHeader, timestampSeconds);
            return VerificationResult.rejected("Duplicate or replayed webhook request");
        }

        return VerificationResult.ok();
    }

    private static byte[] buildCanonicalPayload(String timestamp, byte[] rawBodyBytes) {
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] canonical = new byte[prefix.length + rawBodyBytes.length];
        System.arraycopy(prefix, 0, canonical, 0, prefix.length);
        System.arraycopy(rawBodyBytes, 0, canonical, prefix.length, rawBodyBytes.length);
        return canonical;
    }

    private void pruneExpiredHashes(long nowSeconds) {
        seenRequestHashes.entrySet().removeIf(entry -> entry.getValue() <= nowSeconds);
    }

    private static String sha256Hex(byte[] canonicalBytes, byte[] signatureBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonicalBytes);
            digest.update((byte) ':');
            digest.update(signatureBytes);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public record VerificationResult(boolean valid, String reason) {
        public static VerificationResult ok() {
            return new VerificationResult(true, null);
        }

        public static VerificationResult rejected(String reason) {
            return new VerificationResult(false, reason);
        }
    }
}
