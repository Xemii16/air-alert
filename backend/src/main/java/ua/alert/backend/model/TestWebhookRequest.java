package ua.alert.backend.model;

import java.util.List;

public class TestWebhookRequest {

    private String regionId = "31";
    private String regionName = "м. Київ";
    private String status;
    private String alarmType;
    private String alertType = "AIR";
    private String alertLevel = "Red";
    private String reason = "";
    private String createdAt;
    private List<ActiveAlertLevelDto> activeAlertLevels;
    private boolean active = true;

    public TestWebhookRequest() {
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAlarmType() {
        return alarmType;
    }

    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
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

    public List<ActiveAlertLevelDto> getActiveAlertLevels() {
        return activeAlertLevels;
    }

    public void setActiveAlertLevels(List<ActiveAlertLevelDto> activeAlertLevels) {
        this.activeAlertLevels = activeAlertLevels;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
