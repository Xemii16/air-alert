package ua.alert.backend.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertDto {

    private String regionId;

    @JsonAlias({"type", "alertType"})
    private String alertType;

    private String startDate;
    private String endDate;
    private String changedDate;

    @JsonAlias({"lastUpdate"})
    private String lastUpdate;

    public AlertDto() {
    }

    public AlertDto(String regionId, String alertType, String startDate, String endDate) {
        this.regionId = regionId;
        this.alertType = alertType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getRegionId() {
        return regionId;
    }

    public void setRegionId(String regionId) {
        this.regionId = regionId;
    }

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getChangedDate() {
        return changedDate;
    }

    public void setChangedDate(String changedDate) {
        this.changedDate = changedDate;
    }

    public String getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(String lastUpdate) {
        this.lastUpdate = lastUpdate;
    }
}
