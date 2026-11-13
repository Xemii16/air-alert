package ua.alert.backend.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.model.DeviceRegistrationRequest;

@Service
public class DeviceSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceSubscriptionService.class);

    private final RegionHierarchyService hierarchyService;
    private final Map<String, DeviceRegistrationRequest> devicesById = new ConcurrentHashMap<>();
    private final Map<String, AlertRegionModel> activeAlertByRegion = new ConcurrentHashMap<>();

    public DeviceSubscriptionService(RegionHierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    public void registerDevice(DeviceRegistrationRequest request) {
        devicesById.put(request.getDeviceId(), request);
        log.info("Registered device {} for region {} ({})",
                request.getDeviceId(), request.getEffectiveRegionId(), request.getRegionName());
    }

    public Collection<DeviceRegistrationRequest> getAllDevices() {
        return devicesById.values();
    }

    /**
     * Finds all registered FCM tokens whose subscribed region (State, District, or Community)
     * is affected by an alert in [webhookRegionId].
     */
    public Set<String> findMatchingFcmTokens(String webhookRegionId) {
        Set<String> affectedIds = hierarchyService.getAffectedRegionIds(webhookRegionId);
        Set<String> tokens = new LinkedHashSet<>();

        for (DeviceRegistrationRequest device : devicesById.values()) {
            if (device.getFcmToken() == null || device.getFcmToken().isBlank()) {
                continue;
            }
            String effectiveId = device.getEffectiveRegionId();
            Set<String> deviceLineage = hierarchyService.getLineageIds(effectiveId);
            if (device.getStateId() != null) {
                deviceLineage.add(device.getStateId());
            }
            if (device.getDistrictId() != null) {
                deviceLineage.add(device.getDistrictId());
            }
            if (device.getCommunityId() != null) {
                deviceLineage.add(device.getCommunityId());
            }

            boolean matches = affectedIds.contains(effectiveId)
                    || deviceLineage.contains(webhookRegionId);
            if (matches) {
                tokens.add(device.getFcmToken());
            }
        }
        return tokens;
    }

    public void updateAlertState(AlertRegionModel model, boolean isActive) {
        if (model == null || model.getRegionId() == null) {
            return;
        }
        if (isActive) {
            activeAlertByRegion.put(model.getRegionId(), model);
        } else {
            activeAlertByRegion.remove(model.getRegionId());
        }
    }

    public AlertRegionModel getActiveAlertForRegion(String regionId) {
        Set<String> lineage = hierarchyService.getLineageIds(regionId);
        for (String id : lineage) {
            AlertRegionModel active = activeAlertByRegion.get(id);
            if (active != null) {
                return active;
            }
        }
        return null;
    }

    public List<AlertRegionModel> getAllActiveAlerts() {
        return List.copyOf(activeAlertByRegion.values());
    }
}
