package ua.alert.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Matches the official UkraineAlarm WebHookMessageModel payload:
 * status/alertLevel/reason describe the current change, while activeAlertLevels
 * holds the complete remaining active snapshot for (regionId, alarmType).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WebHookMessageModel {

    private String status;
    private String regionId;

    @JsonAlias({"alarmType", "type", "alertType"})
    private String alarmType;

    @JsonAlias({"createdAt", "lastUpdate"})
    private String createdAt;

    private String alertLevel;
    private String reason;
    private List<ActiveAlertLevelDto> activeAlertLevels = new ArrayList<>();

    public WebHookMessageModel() {
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
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

    public List<ActiveAlertLevelDto> getActiveAlertLevels() {
        return activeAlertLevels;
    }

    public void setActiveAlertLevels(List<ActiveAlertLevelDto> activeAlertLevels) {
        this.activeAlertLevels = activeAlertLevels != null ? activeAlertLevels : new ArrayList<>();
    }
}
