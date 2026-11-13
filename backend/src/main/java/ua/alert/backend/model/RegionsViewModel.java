package ua.alert.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RegionsViewModel {

    private List<RegionViewModel> states = new ArrayList<>();

    public RegionsViewModel() {
    }

    public RegionsViewModel(List<RegionViewModel> states) {
        this.states = states != null ? states : new ArrayList<>();
    }

    public List<RegionViewModel> getStates() {
        return states;
    }

    public void setStates(List<RegionViewModel> states) {
        this.states = states != null ? states : new ArrayList<>();
    }
}
