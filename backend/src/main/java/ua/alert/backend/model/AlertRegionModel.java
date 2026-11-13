package ua.alert.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertRegionModel {

    private String regionId;
    private String regionType;
    private String regionName;
    private String regionEngName;

    @JsonAlias({"lastUpdate", "createdAt"})
    private String lastUpdate;

    private List<AlertDto> activeAlerts = new ArrayList<>();

    // Optional fields if webhook sends direct AlertModification schema (status: "Activate"/"Deactivate", type: "AIR")
    private String status;

    @JsonAlias({"type", "alertType"})
    private String alertType;

    public AlertRegionModel() {
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getRegionType() {
        return regionType;
    }

    public void setRegionType(String regionType) {
        this.regionType = regionType;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getRegionEngName() {
        return regionEngName;
    }

    public void setRegionEngName(String regionEngName) {
        this.regionEngName = regionEngName;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public List<AlertDto> getActiveAlerts() {
        return activeAlerts;
    }

    public void setActiveAlerts(List<AlertDto> activeAlerts) {
        this.activeAlerts = activeAlerts != null ? activeAlerts : new ArrayList<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }
}
