package com.pim.backend.lov.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A reusable list of values (ERF 1.1 §3.2.1; data-model "ListOfValues"). Immutable: every change returns a new list,
 * which the service stores as one document so the change is all or nothing (research R-02).
 *
 * @param systemId feature 003 system ID of type {@code LIST_OF_VALUES}; never changes (FR-001)
 * @param name unique among lists ignoring letter case (FR-003)
 * @param equivalencesEnabled whether the equivalence column is shown; turning it off keeps the equivalences (FR-023)
 * @param values in the order they were added; IDs unique ignoring case (FR-008); at most {@value #MAX_VALUES}
 * @param nextGeneratedNumber counter for generated IDs; never decreases (research R-07)
 */
public record ListOfValues(
        String systemId,
        ListName name,
        boolean equivalencesEnabled,
        List<ListValue> values,
        long nextGeneratedNumber,
        Instant createdAt,
        Instant updatedAt) {

    /** Most values a list may hold (research R-02, R-08). */
    public static final int MAX_VALUES = 50_000;

    public ListOfValues {
        values = List.copyOf(values);
        if (values.size() > MAX_VALUES) {
            throw new ListTooLargeException();
        }
    }

    /** A new list with no values. */
    public static ListOfValues create(String systemId, ListName name, Instant now) {
        return new ListOfValues(systemId, name, false, List.of(), 1, now, now);
    }

    /** The value with this ID, compared ignoring letter case; empty for an unknown or invalid ID. */
    public Optional<ListValue> find(String valueId) {
        if (valueId == null) {
            return Optional.empty();
        }
        String key = ValueId.keyOf(valueId);
        return values.stream().filter(value -> value.id().key().equals(key)).findFirst();
    }

    /** The comparison keys of every value ID in the list. */
    public Set<String> idKeys() {
        Set<String> keys = new HashSet<>();
        values.forEach(value -> keys.add(value.id().key()));
        return keys;
    }

    /**
     * Adds one value at the end (FR-009, FR-010). A {@code null} or blank ID is generated (research R-07).
     *
     * @throws InvalidListException for an invalid ID, label or equivalence
     * @throws ValueIdInUseException when the list already has the ID, ignoring letter case
     * @throws ListTooLargeException when the list already has {@value #MAX_VALUES} values
     */
    public ValueAddition withValueAdded(String id, String label, String equivalence, Instant now) {
        String normalizedLabel = ListValue.label(label);
        Optional<String> normalizedEquivalence = ListValue.equivalence(equivalence);
        long counter = nextGeneratedNumber;
        ValueId valueId;
        if (id == null || id.isBlank()) {
            long number = GeneratedValueIds.firstFree(nextGeneratedNumber, idKeys());
            valueId = ValueId.of(GeneratedValueIds.format(number));
            counter = number + 1;
        } else {
            valueId = ValueId.of(id);
            if (idKeys().contains(valueId.key())) {
                throw new ValueIdInUseException(valueId);
            }
        }
        if (values.size() >= MAX_VALUES) {
            throw new ListTooLargeException();
        }
        ListValue value = new ListValue(valueId, normalizedLabel, normalizedEquivalence);
        List<ListValue> changed = new ArrayList<>(values);
        changed.add(value);
        return new ValueAddition(
                new ListOfValues(systemId, name, equivalencesEnabled, changed, counter, createdAt, now), value);
    }

    /**
     * Replaces the label and equivalence of one value; its ID never changes (FR-010, FR-012).
     *
     * @throws ValueNotFoundException if the list has no value with the ID
     * @throws InvalidListException for an invalid label or equivalence
     */
    public ListOfValues withValueUpdated(String valueId, String label, String equivalence, Instant now) {
        ListValue existing = find(valueId).orElseThrow(() -> new ValueNotFoundException(valueId));
        ListValue updated = new ListValue(existing.id(), ListValue.label(label), ListValue.equivalence(equivalence));
        List<ListValue> changed = values.stream()
                .map(value -> value.id().equals(existing.id()) ? updated : value)
                .toList();
        return new ListOfValues(systemId, name, equivalencesEnabled, changed, nextGeneratedNumber, createdAt, now);
    }

    /**
     * Removes one value (FR-013). The counter of generated IDs is kept, so a generated ID is never reused.
     *
     * @throws ValueNotFoundException if the list has no value with the ID
     */
    public ListOfValues withValueRemoved(String valueId, Instant now) {
        ListValue existing = find(valueId).orElseThrow(() -> new ValueNotFoundException(valueId));
        List<ListValue> changed = values.stream()
                .filter(value -> !value.id().equals(existing.id()))
                .toList();
        return new ListOfValues(systemId, name, equivalencesEnabled, changed, nextGeneratedNumber, createdAt, now);
    }

    /**
     * Replaces every value and the counter at once (bulk load, FR-011).
     *
     * @throws ListTooLargeException when there are more than {@value #MAX_VALUES} values
     */
    public ListOfValues withValues(List<ListValue> newValues, long newNextGeneratedNumber, Instant now) {
        return new ListOfValues(systemId, name, equivalencesEnabled, newValues, newNextGeneratedNumber, createdAt, now);
    }

    /** The list with another name; ID, values and links stay (User Story 3). */
    public ListOfValues renamed(ListName newName, Instant now) {
        return new ListOfValues(systemId, newName, equivalencesEnabled, values, nextGeneratedNumber, createdAt, now);
    }

    /** The list with the equivalence column on or off; the equivalences themselves are kept (FR-023). */
    public ListOfValues withEquivalencesEnabled(boolean enabled, Instant now) {
        return new ListOfValues(systemId, name, enabled, values, nextGeneratedNumber, createdAt, now);
    }
}
