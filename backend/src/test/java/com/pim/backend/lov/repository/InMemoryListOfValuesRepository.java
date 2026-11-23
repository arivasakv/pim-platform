package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test fake: lists of values kept in memory, with the same look-up rules as the MongoDB repository. */
public class InMemoryListOfValuesRepository implements ListOfValuesRepository {

    private final Map<String, ListOfValues> byId = new ConcurrentHashMap<>();

    @Override
    public Map<String, String> namesById() {
        Map<String, String> names = new java.util.HashMap<>();
        byId.forEach((key, list) -> names.put(key, list.name().value()));
        return Map.copyOf(names);
    }

    @Override
    public Optional<ListOfValues> findById(String systemId) {
        return systemId == null ? Optional.empty() : Optional.ofNullable(byId.get(key(systemId)));
    }

    @Override
    public List<ListOfValues> findAll() {
        return byId.values().stream()
                .sorted(Comparator.comparing(list -> list.name().comparisonKey()))
                .toList();
    }

    @Override
    public Optional<ListOfValues> findByName(ListName name) {
        return byId.values().stream().filter(list -> list.name().equals(name)).findFirst();
    }

    @Override
    public boolean existsWithName(ListName name, String excludedId) {
        return byId.values().stream()
                .anyMatch(list -> list.name().equals(name)
                        && (excludedId == null || !key(list.systemId()).equals(key(excludedId))));
    }

    @Override
    public boolean hasAny() {
        return !byId.isEmpty();
    }

    @Override
    public void insert(ListOfValues list) {
        if (byId.putIfAbsent(key(list.systemId()), list) != null) {
            throw new IllegalStateException("List already stored: " + list.systemId());
        }
    }

    @Override
    public void update(ListOfValues list) {
        byId.put(key(list.systemId()), list);
    }

    @Override
    public void delete(String systemId) {
        byId.remove(key(systemId));
    }

    private static String key(String systemId) {
        return systemId.toUpperCase(Locale.ROOT);
    }
}
