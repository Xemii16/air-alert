package ua.alert.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RegionViewModel {

    private String regionId;
    private String regionName;
    private String regionType;
    private List<RegionViewModel> regionChildIds = new ArrayList<>();

    public RegionViewModel() {
    }

    public RegionViewModel(String regionId, String regionName, String regionType, List<RegionViewModel> regionChildIds) {
        this.regionId = regionId;
        this.regionName = regionName;
        this.regionType = regionType;
        this.regionChildIds = regionChildIds != null ? regionChildIds : new ArrayList<>();
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

    public String getRegionType() {
        return regionType;
    }

    public void setRegionType(String regionType) {
        this.regionType = regionType;
    }

    public List<RegionViewModel> getRegionChildIds() {
        return regionChildIds;
    }

    public void setRegionChildIds(List<RegionViewModel> regionChildIds) {
        this.regionChildIds = regionChildIds != null ? regionChildIds : new ArrayList<>();
    }
}
