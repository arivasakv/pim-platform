package com.pim.backend.taxonomy.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The values of a list-of-values attribute included (allowed) at a taxonomy node (feature 012 FR-001, FR-002;
 * data-model "List-value filter"). It applies to the node and to every descendant (FR-007).
 *
 * @param nodeId the node's system ID, upper-cased
 * @param attributeId the list-of-values attribute's system ID, upper-cased
 * @param valueIds 1–{@value #MAX_VALUES} value IDs of the attribute's list, each once ignoring letter case
 * @param createdAt when the filter was first saved
 * @param updatedAt when its values last changed
 */
public record ValueFilter(
        String nodeId, String attributeId, List<String> valueIds, Instant createdAt, Optional<Instant> updatedAt) {

    /** Most values a filter may include: a list's maximum (feature 006). */
    public static final int MAX_VALUES = 50_000;

    public ValueFilter {
        nodeId = TaxonomyNode.key(Objects.requireNonNull(nodeId, "nodeId"));
        attributeId = TaxonomyNode.key(Objects.requireNonNull(attributeId, "attributeId"));
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        valueIds = distinct(valueIds);
        if (valueIds.isEmpty() || valueIds.size() > MAX_VALUES) {
            throw new InvalidFilterException();
        }
    }

    /**
     * A new filter.
     *
     * @throws InvalidFilterException {@code INVALID_FILTER_VALUES} with no value or more than {@value #MAX_VALUES}
     */
    public static ValueFilter create(String nodeId, String attributeId, List<String> valueIds, Instant at) {
        return new ValueFilter(nodeId, attributeId, valueIds, at, Optional.empty());
    }

    /**
     * Checks the number of value IDs a request names before anything else is read (data-model rule 2).
     *
     * @throws InvalidFilterException {@code INVALID_FILTER_VALUES} with no value or more than {@value #MAX_VALUES}
     */
    public static void checkCount(List<String> valueIds) {
        if (valueIds == null) {
            throw new InvalidFilterException();
        }
        List<String> unique = distinct(valueIds);
        if (unique.isEmpty() || unique.size() > MAX_VALUES) {
            throw new InvalidFilterException();
        }
    }

    /** The comparison key of a value ID: trimmed and upper-cased, as feature 006 compares IDs. */
    public static String valueKey(String valueId) {
        return valueId.strip().toUpperCase(Locale.ROOT);
    }

    /** A copy with other values; node, attribute and creation date stay. */
    public ValueFilter withValues(List<String> newValueIds, Instant at) {
        return new ValueFilter(nodeId, attributeId, newValueIds, createdAt, Optional.of(at));
    }

    /** The storage key: {@code NODE:ATTRIBUTE}. */
    public String id() {
        return nodeId + ":" + attributeId;
    }

    /** The comparison keys of the included value IDs. */
    public Set<String> valueKeys() {
        Set<String> keys = new HashSet<>();
        valueIds.forEach(id -> keys.add(valueKey(id)));
        return keys;
    }

    private static List<String> distinct(List<String> ids) {
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            if (id != null && !id.isBlank() && seen.add(valueKey(id))) {
                result.add(id.strip());
            }
        }
        return List.copyOf(result);
    }
}
