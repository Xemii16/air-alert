package ua.alert.backend.service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ua.alert.backend.client.UkraineAlarmApiClient;
import ua.alert.backend.model.RegionViewModel;
import ua.alert.backend.model.RegionsViewModel;

@Service
public class RegionHierarchyService {

    private static final Logger log = LoggerFactory.getLogger(RegionHierarchyService.class);

    private final UkraineAlarmApiClient apiClient;
    private volatile RegionsViewModel cachedRegions = new RegionsViewModel();
    private volatile Map<String, RegionViewModel> regionById = Collections.emptyMap();
    private volatile Map<String, String> parentById = Collections.emptyMap();
    private volatile Map<String, Set<String>> descendantsById = Collections.emptyMap();

    public RegionHierarchyService(UkraineAlarmApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @PostConstruct
    public void initDefaultHierarchy() {
        indexHierarchy(buildDefaultRegionsHierarchy());
        log.info("Loaded {} default Ukrainian administrative regions; live sync will run after startup", regionById.size());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (apiClient.isConfigured()) {
            CompletableFuture.runAsync(this::refreshHierarchy);
        }
    }

    public void refreshHierarchy() {
        RegionsViewModel fetched = apiClient.fetchRegions();
        if (fetched != null && fetched.getStates() != null && !fetched.getStates().isEmpty()) {
            indexHierarchy(fetched);
            log.info("Indexed {} live Ukrainian administrative regions from UkraineAlarm API", regionById.size());
        }
    }

    public synchronized void indexHierarchy(RegionsViewModel viewModel) {
        if (viewModel.getStates() == null) {
            this.cachedRegions = viewModel;
            this.regionById = Collections.emptyMap();
            this.parentById = Collections.emptyMap();
            this.descendantsById = Collections.emptyMap();
            return;
        }
        Map<String, RegionViewModel> nextRegionById = new HashMap<>();
        Map<String, String> nextParentById = new HashMap<>();
        Map<String, Set<String>> nextDescendantsById = new HashMap<>();

        for (RegionViewModel state : viewModel.getStates()) {
            indexNode(state, null, nextRegionById, nextParentById, nextDescendantsById);
        }

        this.cachedRegions = viewModel;
        this.regionById = Collections.unmodifiableMap(nextRegionById);
        this.parentById = Collections.unmodifiableMap(nextParentById);
        this.descendantsById = Collections.unmodifiableMap(nextDescendantsById);
    }

    private Set<String> indexNode(
            RegionViewModel node,
            String parentId,
            Map<String, RegionViewModel> nextRegionById,
            Map<String, String> nextParentById,
            Map<String, Set<String>> nextDescendantsById
    ) {
        String id = node.getRegionId();
        nextRegionById.put(id, node);
        if (parentId != null) {
            nextParentById.put(id, parentId);
        }
        Set<String> allDescendants = new LinkedHashSet<>();
        if (node.getRegionChildIds() != null) {
            for (RegionViewModel child : node.getRegionChildIds()) {
                allDescendants.add(child.getRegionId());
                allDescendants.addAll(indexNode(child, id, nextRegionById, nextParentById, nextDescendantsById));
            }
        }
        nextDescendantsById.put(id, Collections.unmodifiableSet(allDescendants));
        return allDescendants;
    }

    public RegionsViewModel getRegionsTree() {
        return cachedRegions;
    }

    public String getRegionName(String regionId, String fallbackName) {
        RegionViewModel model = regionById.get(regionId);
        if (model != null && model.getRegionName() != null && !model.getRegionName().isBlank()) {
            return model.getRegionName();
        }
        return (fallbackName != null && !fallbackName.isBlank()) ? fallbackName : "Україна";
    }

    /**
     * Returns all regionIds affected by an event on [regionId]:
     * the region itself plus all child Districts and Communities inside it.
     */
    public Set<String> getAffectedRegionIds(String regionId) {
        Set<String> result = new LinkedHashSet<>();
        if (regionId == null || regionId.isBlank()) {
            return result;
        }
        result.add(regionId);
        Set<String> descendants = descendantsById.get(regionId);
        if (descendants != null) {
            result.addAll(descendants);
        }
        return result;
    }

    /**
     * Returns the lineage (Community -> District -> State) for a given regionId.
     */
    public Set<String> getLineageIds(String regionId) {
        Set<String> result = new LinkedHashSet<>();
        String current = regionId;
        while (current != null && !result.contains(current)) {
            result.add(current);
            current = parentById.get(current);
        }
        return result;
    }

    public static RegionsViewModel buildDefaultRegionsHierarchy() {
        List<RegionViewModel> states = new ArrayList<>();

        states.add(new RegionViewModel("31", "м. Київ", "State", Collections.emptyList()));

        List<RegionViewModel> kyivOblDistricts = List.of(
                new RegionViewModel("64", "Бучанський район", "District", List.of(
                        new RegionViewModel("665", "Бучанська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("669", "Ірпінська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("667", "Гостомельська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("666", "Вишнева територіальна громада", "Community", Collections.emptyList())
                )),
                new RegionViewModel("62", "Білоцерківський район", "District", List.of(
                        new RegionViewModel("640", "Білоцерківська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("650", "Узинська територіальна громада", "Community", Collections.emptyList())
                )),
                new RegionViewModel("63", "Броварський район", "District", List.of(
                        new RegionViewModel("656", "Броварська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("655", "Березанська територіальна громада", "Community", Collections.emptyList())
                )),
                new RegionViewModel("65", "Вишгородський район", "District", List.of(
                        new RegionViewModel("677", "Вишгородська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("682", "Славутицька територіальна громада", "Community", Collections.emptyList())
                )),
                new RegionViewModel("66", "Обухівський район", "District", List.of(
                        new RegionViewModel("688", "Обухівська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("684", "Васильківська територіальна громада", "Community", Collections.emptyList())
                ))
        );
        states.add(new RegionViewModel("14", "Київська область", "State", kyivOblDistricts));

        states.add(new RegionViewModel("22", "Харківська область", "State", List.of(
                new RegionViewModel("125", "Харківський район", "District", List.of(
                        new RegionViewModel("1483", "м. Харків та Харківська територіальна громада", "Community", Collections.emptyList()),
                        new RegionViewModel("1474", "Дергачівська територіальна громада", "Community", Collections.emptyList())
                )),
                new RegionViewModel("126", "Чугуївський район", "District", List.of(
                        new RegionViewModel("1494", "Чугуївська територіальна громада", "Community", Collections.emptyList())
                ))
        )));

        states.add(new RegionViewModel("9", "Дніпропетровська область", "State", List.of(
                new RegionViewModel("41", "Дніпровський район", "District", List.of(
                        new RegionViewModel("399", "м. Дніпро та Дніпровська територіальна громада", "Community", Collections.emptyList())
                )),
                new RegionViewModel("43", "Криворізький район", "District", List.of(
                        new RegionViewModel("427", "м. Кривий Ріг та Криворізька територіальна громада", "Community", Collections.emptyList())
                ))
        )));

        states.add(new RegionViewModel("27", "Львівська область", "State", List.of(
                new RegionViewModel("77", "Львівський район", "District", List.of(
                        new RegionViewModel("855", "м. Львів та Львівська територіальна громада", "Community", Collections.emptyList())
                ))
        )));

        states.add(new RegionViewModel("16", "Одеська область", "State", List.of(
                new RegionViewModel("94", "Одеський район", "District", List.of(
                        new RegionViewModel("1068", "м. Одеса та Одеська територіальна громада", "Community", Collections.emptyList())
                ))
        )));

        states.add(new RegionViewModel("12", "Запорізька область", "State", List.of(
                new RegionViewModel("53", "Запорізький район", "District", List.of(
                        new RegionViewModel("533", "м. Запоріжжя та Запорізька територіальна громада", "Community", Collections.emptyList())
                ))
        )));

        states.add(new RegionViewModel("4", "Вінницька область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("8", "Волинська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("28", "Донецька область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("10", "Житомирська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("11", "Закарпатська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("13", "Івано-Франківська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("15", "Кіровоградська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("17", "Миколаївська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("19", "Полтавська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("5", "Рівненська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("20", "Сумська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("21", "Тернопільська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("23", "Херсонська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("3", "Хмельницька область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("24", "Черкаська область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("26", "Чернівецька область", "State", Collections.emptyList()));
        states.add(new RegionViewModel("25", "Чернігівська область", "State", Collections.emptyList()));

        return new RegionsViewModel(states);
    }
}
