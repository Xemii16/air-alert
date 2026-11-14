package ua.alert.backend.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import ua.alert.backend.model.ActiveAlertLevelDto;
import ua.alert.backend.model.AlertDto;
import ua.alert.backend.model.AlertRegionModel;
import ua.alert.backend.model.WebHookMessageModel;

/**
 * Maintains snapshot-based alert state keyed by (regionId, alarmType).
 * activeAlertLevels from each WebHookMessageModel replaces the stored snapshot
 * for that (regionId, alarmType) rather than relying on status strings alone.
 */
@Service
public class AlertStateService {

    public static final String ALARM_TYPE_INFO = "INFO";
    public static final String ALARM_TYPE_ALL_CLEAR = "ALL_CLEAR";

    public record RegionAlertSnapshot(
            String regionId,
            String regionName,
            boolean isActive,
            String dangerLevel,
            String primaryAlarmType,
            List<ActiveAlertLevelDto> activeAlertLevels
    ) {
    }

    private final RegionHierarchyService hierarchyService;
    private final Map<String, ConcurrentHashMap<String, List<ActiveAlertLevelDto>>> snapshotsByRegionAndType =
            new ConcurrentHashMap<>();

    public AlertStateService(RegionHierarchyService hierarchyService) {
        this.hierarchyService = hierarchyService;
    }

    public static boolean isIgnoredAlarmType(String alarmType) {
        return alarmType != null && ALARM_TYPE_INFO.equalsIgnoreCase(alarmType.trim());
    }

    public static String normalizeAlarmType(String alarmType) {
        if (alarmType == null || alarmType.isBlank()) {
            return "AIR";
        }
        return alarmType.trim().toUpperCase();
    }

    /**
     * Replaces the stored snapshot for (regionId, alarmType) using msg.activeAlertLevels
     * and returns the combined snapshot across all remaining active alarmTypes for regionId.
     * Returns null when the message is invalid or uses an ignored alarmType (INFO).
     */
    public RegionAlertSnapshot applyWebhookSnapshot(WebHookMessageModel msg, String resolvedRegionName) {
        if (msg == null || msg.getRegionId() == null || msg.getRegionId().isBlank()) {
            return null;
        }

        String regionId = msg.getRegionId().trim();
        String alarmType = normalizeAlarmType(msg.getAlarmType());
        if (isIgnoredAlarmType(alarmType)) {
            return null;
        }

        List<ActiveAlertLevelDto> incoming = sanitizeLevels(msg.getActiveAlertLevels(), regionId, alarmType);

        snapshotsByRegionAndType.compute(regionId, (id, byType) -> {
            if (incoming.isEmpty()) {
                if (byType == null) {
                    return null;
                }
                byType.remove(alarmType);
                return byType.isEmpty() ? null : byType;
            }
            ConcurrentHashMap<String, List<ActiveAlertLevelDto>> updated =
                    byType != null ? byType : new ConcurrentHashMap<>();
            updated.put(alarmType, incoming);
            return updated;
        });

        return buildRegionSnapshot(regionId, resolvedRegionName, alarmType);
    }

    /**
     * Cold-start fallback for UkraineAlarm REST /api/v3/alerts/{regionId}.
     */
    public void applyRestApiAlerts(String requestedRegionId, List<AlertRegionModel> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        for (AlertRegionModel model : models) {
            if (model == null) {
                continue;
            }
            String regionId = (model.getRegionId() != null && !model.getRegionId().isBlank())
                    ? model.getRegionId().trim()
                    : requestedRegionId;
            if (regionId == null || regionId.isBlank()) {
                continue;
            }

            Map<String, List<ActiveAlertLevelDto>> grouped = new LinkedHashMap<>();
            if (model.getActiveAlerts() != null) {
                for (AlertDto dto : model.getActiveAlerts()) {
                    if (dto == null) {
                        continue;
                    }
                    if (dto.getEndDate() != null && !dto.getEndDate().isBlank()) {
                        continue;
                    }
                    String alarmType = normalizeAlarmType(dto.getAlertType());
                    if (isIgnoredAlarmType(alarmType)) {
                        continue;
                    }
                    String defaultLevel = ("CHEMICAL".equals(alarmType) || "NUCLEAR".equals(alarmType))
                            ? "Yellow"
                            : "Red";
                    String created = dto.getLastUpdate() != null ? dto.getLastUpdate()
                            : (dto.getStartDate() != null ? dto.getStartDate() : "");
                    grouped.computeIfAbsent(alarmType, k -> new ArrayList<>())
                            .add(new ActiveAlertLevelDto(defaultLevel, "", created, alarmType, regionId));
                }
            }

            if (grouped.isEmpty()) {
                snapshotsByRegionAndType.remove(regionId);
            } else {
                ConcurrentHashMap<String, List<ActiveAlertLevelDto>> map = new ConcurrentHashMap<>();
                grouped.forEach((type, items) -> map.put(type, List.copyOf(items)));
                snapshotsByRegionAndType.put(regionId, map);
            }
        }
    }

    public RegionAlertSnapshot getAggregatedSnapshotForLineage(String regionId, String resolvedRegionName) {
        Set<String> lineage = hierarchyService.getLineageIds(regionId);
        List<ActiveAlertLevelDto> combined = new ArrayList<>();
        for (String id : lineage) {
            Map<String, List<ActiveAlertLevelDto>> byType = snapshotsByRegionAndType.get(id);
            if (byType != null) {
                for (List<ActiveAlertLevelDto> items : byType.values()) {
                    combined.addAll(items);
                }
            }
        }
        return summarizeSnapshot(regionId, resolvedRegionName, combined, null);
    }

    public boolean hasActiveAlertForRegion(String regionId) {
        return getAggregatedSnapshotForLineage(regionId, "").isActive();
    }

    public void clearAll() {
        snapshotsByRegionAndType.clear();
    }

    private RegionAlertSnapshot buildRegionSnapshot(
            String regionId,
            String resolvedRegionName,
            String preferredAlarmType
    ) {
        Map<String, List<ActiveAlertLevelDto>> byType = snapshotsByRegionAndType.get(regionId);
        List<ActiveAlertLevelDto> combined = new ArrayList<>();
        if (byType != null) {
            for (List<ActiveAlertLevelDto> items : byType.values()) {
                combined.addAll(items);
            }
        }
        return summarizeSnapshot(regionId, resolvedRegionName, combined, preferredAlarmType);
    }

    private RegionAlertSnapshot summarizeSnapshot(
            String regionId,
            String resolvedRegionName,
            List<ActiveAlertLevelDto> items,
            String preferredAlarmType
    ) {
        if (items.isEmpty()) {
            return new RegionAlertSnapshot(
                    regionId,
                    resolvedRegionName,
                    false,
                    "GREEN",
                    ALARM_TYPE_ALL_CLEAR,
                    List.of()
            );
        }

        boolean hasRed = items.stream().anyMatch(ActiveAlertLevelDto::isRed);
        String dangerLevel = hasRed ? "RED" : "YELLOW";

        String primaryAlarmType = null;
        if (preferredAlarmType != null) {
            boolean preferredHasHighestSeverity = items.stream()
                    .anyMatch(item -> preferredAlarmType.equalsIgnoreCase(item.getAlarmType())
                            && (!hasRed || item.isRed()));
            if (preferredHasHighestSeverity) {
                primaryAlarmType = preferredAlarmType;
            }
        }
        if (primaryAlarmType == null) {
            primaryAlarmType = items.stream()
                    .filter(item -> !hasRed || item.isRed())
                    .map(ActiveAlertLevelDto::getAlarmType)
                    .filter(t -> t != null && !t.isBlank())
                    .findFirst()
                    .orElse("AIR");
        }

        return new RegionAlertSnapshot(
                regionId,
                resolvedRegionName,
                true,
                dangerLevel,
                primaryAlarmType,
                List.copyOf(items)
        );
    }

    private List<ActiveAlertLevelDto> sanitizeLevels(
            List<ActiveAlertLevelDto> rawList,
            String regionId,
            String alarmType
    ) {
        if (rawList == null || rawList.isEmpty()) {
            return List.of();
        }
        List<ActiveAlertLevelDto> cleaned = new ArrayList<>(rawList.size());
        for (ActiveAlertLevelDto item : rawList) {
            if (item == null) {
                continue;
            }
            ActiveAlertLevelDto contextual = item.withContext(regionId, alarmType);
            if (!isIgnoredAlarmType(contextual.getAlarmType())) {
                cleaned.add(contextual);
            }
        }
        return List.copyOf(cleaned);
    }
}
