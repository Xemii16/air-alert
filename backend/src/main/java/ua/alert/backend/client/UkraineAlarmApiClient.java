package ua.alert.backend.client;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.model.RegionsViewModel;
import ua.alert.backend.model.WebHookModel;

@Component
public class UkraineAlarmApiClient {

    public static final String PROFILE_DEVELOPMENT = "development";
    public static final String PROFILE_PRODUCTION = "production";

    private static final Logger log = LoggerFactory.getLogger(UkraineAlarmApiClient.class);

    private final RestClient restClient;
    private final String apiToken;
    private final String callbackUrl;
    private final boolean autoRegisterWebhook;
    private final Environment environment;

    public UkraineAlarmApiClient(
            String baseUrl,
            String apiToken,
            String callbackUrl,
            boolean autoRegisterWebhook
    ) {
        this(baseUrl, apiToken, callbackUrl, autoRegisterWebhook, null);
    }

    @Autowired
    public UkraineAlarmApiClient(
            @Value("${ukrainealarm.api.base-url:https://api.ukrainealarm.com}") String baseUrl,
            @Value("${ukrainealarm.api.token:}") String apiToken,
            @Value("${ukrainealarm.webhook.callback-url:}") String callbackUrl,
            @Value("${ukrainealarm.webhook.auto-register:false}") boolean autoRegisterWebhook,
            Environment environment
    ) {
        this.apiToken = apiToken != null ? apiToken.trim() : "";
        this.callbackUrl = callbackUrl != null ? callbackUrl.trim() : "";
        this.autoRegisterWebhook = autoRegisterWebhook;
        this.environment = environment;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!isProduction()) {
            log.info("Spring profile is not '{}'; skipping automatic webhook registration with api.ukrainealarm.com.", PROFILE_PRODUCTION);
            return;
        }
        if (autoRegisterWebhook && isConfigured() && !callbackUrl.isEmpty()) {
            CompletableFuture.runAsync(() -> {
                try {
                    registerWebhook(callbackUrl);
                } catch (Exception e) {
                    log.warn("Failed to auto-register webhook with api.ukrainealarm.com on startup: {}", e.getMessage());
                }
            });
        }
    }

    public boolean isProduction() {
        return environment != null && environment.acceptsProfiles(Profiles.of(PROFILE_PRODUCTION));
    }

    public boolean isConfigured() {
        return !apiToken.isEmpty();
    }

    public RegionsViewModel fetchRegions() {
        if (!isConfigured()) {
            log.info("UkraineAlarm API token is not set; using built-in fallback regions hierarchy.");
            return null;
        }
        try {
            return restClient.get()
                    .uri("/api/v3/regions")
                    .header(HttpHeaders.AUTHORIZATION, apiToken)
                    .retrieve()
                    .body(RegionsViewModel.class);
        } catch (Exception e) {
            log.error("Failed to fetch /api/v3/regions from api.ukrainealarm.com", e);
            return null;
        }
    }

    public List<AlertRegionModel> fetchAlertsForRegion(String regionId) {
        if (!isConfigured()) {
            return Collections.emptyList();
        }
        try {
            List<AlertRegionModel> result = restClient.get()
                    .uri("/api/v3/alerts/{regionId}", regionId)
                    .header(HttpHeaders.AUTHORIZATION, apiToken)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AlertRegionModel>>() {});
            return result != null ? result : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch /api/v3/alerts/{} from api.ukrainealarm.com", regionId, e);
            return Collections.emptyList();
        }
    }

    public boolean registerWebhook(String targetWebhookUrl) {
        if (!isProduction()) {
            log.info("Skipping webhook registration because '{}' Spring profile is not active.", PROFILE_PRODUCTION);
            return false;
        }
        if (!isConfigured()) {
            log.warn("Cannot register webhook on api.ukrainealarm.com: API token is empty.");
            return false;
        }
        try {
            restClient.post()
                    .uri("/api/v3/webhook")
                    .header(HttpHeaders.AUTHORIZATION, apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WebHookModel(targetWebhookUrl))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully registered webhook URL with api.ukrainealarm.com: {}", targetWebhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to register webhook with api.ukrainealarm.com", e);
            return false;
        }
    }

    public boolean updateWebhook(String targetWebhookUrl) {
        if (!isProduction()) {
            log.info("Skipping webhook update because '{}' Spring profile is not active.", PROFILE_PRODUCTION);
            return false;
        }
        if (!isConfigured()) {
            return false;
        }
        try {
            restClient.patch()
                    .uri("/api/v3/webhook")
                    .header(HttpHeaders.AUTHORIZATION, apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WebHookModel(targetWebhookUrl))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully updated webhook URL with api.ukrainealarm.com: {}", targetWebhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to update webhook with api.ukrainealarm.com", e);
            return false;
        }
    }

    public boolean deleteWebhook(String targetWebhookUrl) {
        if (!isConfigured()) {
            return false;
        }
        try {
            restClient.method(HttpMethod.DELETE)
                    .uri("/api/v3/webhook")
                    .header(HttpHeaders.AUTHORIZATION, apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new WebHookModel(targetWebhookUrl))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Successfully deleted webhook URL from api.ukrainealarm.com: {}", targetWebhookUrl);
            return true;
        } catch (Exception e) {
            log.error("Failed to delete webhook from api.ukrainealarm.com", e);
            return false;
        }
    }
}
