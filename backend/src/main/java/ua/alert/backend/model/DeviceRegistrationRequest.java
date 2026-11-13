package ua.alert.backend.model;

import jakarta.validation.constraints.NotBlank;

public class DeviceRegistrationRequest {

    @NotBlank
    private String deviceId;

    @NotBlank
    private String fcmToken;

    @NotBlank
    private String stateId;

    private String districtId;
    private String communityId;
    private String regionName;

    public DeviceRegistrationRequest() {
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getStateId() {
        return stateId;
    }

    public void setStateId(String stateId) {
        this.stateId = stateId;
    }

    public String getDistrictId() {
        return districtId;
    }

    public void setDistrictId(String districtId) {
        this.districtId = districtId;
    }

    public String getCommunityId() {
        return communityId;
    }

    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }

    public String getEffectiveRegionId() {
        if (communityId != null && !communityId.isBlank()) {
            return communityId;
        }
        if (districtId != null && !districtId.isBlank()) {
            return districtId;
        }
        return stateId;
    }
}
