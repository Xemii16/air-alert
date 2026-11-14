package ua.alert.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActiveAlertLevelDto {

    private String alertLevel;
    private String reason;
    private String createdAt;
    private String alarmType;
    private String regionId;

    public ActiveAlertLevelDto() {
    }

    public ActiveAlertLevelDto(String alertLevel, String reason, String createdAt) {
        this.alertLevel = alertLevel;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    public ActiveAlertLevelDto(String alertLevel, String reason, String createdAt, String alarmType, String regionId) {
        this.alertLevel = alertLevel;
        this.reason = reason;
        this.createdAt = createdAt;
        this.alarmType = alarmType;
        this.regionId = regionId;
    }

    public ActiveAlertLevelDto withContext(String resolvedRegionId, String resolvedAlarmType) {
        return new ActiveAlertLevelDto(
                normalizeAlertLevel(alertLevel),
                reason != null ? reason.trim() : "",
                createdAt != null ? createdAt.trim() : "",
                (alarmType != null && !alarmType.isBlank()) ? alarmType.trim().toUpperCase() : resolvedAlarmType,
                (regionId != null && !regionId.isBlank()) ? regionId.trim() : resolvedRegionId
        );
    }

    public static String normalizeAlertLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "Red";
        }
        String trimmed = raw.trim();
        if ("yellow".equalsIgnoreCase(trimmed)) {
            return "Yellow";
        }
        if ("red".equalsIgnoreCase(trimmed)) {
            return "Red";
        }
        return trimmed;
    }

    public boolean isRed() {
        return "red".equalsIgnoreCase(alertLevel);
    }

    public String getAlertLevel() {
        return alertLevel;
    }

    public void setAlertLevel(String alertLevel) {
        this.alertLevel = alertLevel;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }
}
