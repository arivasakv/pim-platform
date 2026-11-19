package com.pim.backend.taxonomy.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The values of one list-of-values attribute allowed at a node (feature 012 FR-007 to FR-011; data-model "Allowed
 * values"). Pure: it reads the node's chain, the list's values and the filters on the chain, so every read reflects
 * the current tree, filters and list (FR-012).
 *
 * @param allowed list values included in every filter on the chain, in list order (all values with no filter)
 * @param availableAbove the same using only the ancestors' filters: what the node's own filter may include (FR-009)
 * @param ownFilter the node's own filter, if any
 * @param includedValues the own filter's values that are still in the list, in list order
 * @param excludedAbove own filter values no longer allowed above, each with the nearest ancestor excluding it
 * @param involved the filters on the chain, from the top level down, with their included counts
 */
public record AllowedValues(
        List<ListValueOption> allowed,
        List<ListValueOption> availableAbove,
        Optional<ValueFilter> ownFilter,
        List<ListValueOption> includedValues,
        List<ExcludedValue> excludedAbove,
        List<FilterOnPath> involved) {

    /** A value of the node's own filter that a filter above does not allow (FR-010). */
    public record ExcludedValue(ListValueOption value, NodeReference excludedBy) {}

    /** A filter on the node's chain and how many of its values are still in the list (FR-013). */
    public record FilterOnPath(NodeReference node, int includedCount) {}

    public AllowedValues {
        allowed = List.copyOf(allowed);
        availableAbove = List.copyOf(availableAbove);
        Objects.requireNonNull(ownFilter, "ownFilter");
        includedValues = List.copyOf(includedValues);
        excludedAbove = List.copyOf(excludedAbove);
        involved = List.copyOf(involved);
    }

    /**
     * Computes the allowed values at the last node of {@code chain}.
     *
     * @param chain the node's ancestors from the top level down, then the node ({@link EffectiveAttributes#chain})
     * @param values the attribute's list values, in list order
     * @param filtersByNodeId the filters for this attribute, keyed by {@link TaxonomyNode#id()}; others are ignored
     */
    public static AllowedValues of(
            List<TaxonomyNode> chain, List<ListValueOption> values, Map<String, ValueFilter> filtersByNodeId) {
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("The chain must contain the node");
        }
        TaxonomyNode self = chain.get(chain.size() - 1);
        Set<String> listKeys = values.stream().map(ListValueOption::key).collect(Collectors.toSet());
        // Each filter's keys are built once: filters and lists can hold 50,000 values (SC-005).
        Map<String, Set<String>> keysByNode = keysByNode(chain, filtersByNodeId);

        List<ListValueOption> availableAbove = new ArrayList<>(values);
        List<FilterOnPath> involved = new ArrayList<>();
        for (int depth = 0; depth < chain.size(); depth++) {
            Set<String> keys = keysByNode.get(chain.get(depth).id());
            if (keys == null) {
                continue;
            }
            int stillInList = (int) keys.stream().filter(listKeys::contains).count();
            involved.add(new FilterOnPath(chain.get(depth).reference(), stillInList));
            if (depth < chain.size() - 1) {
                availableAbove.removeIf(value -> !keys.contains(value.key()));
            }
        }

        Optional<ValueFilter> own = Optional.ofNullable(filtersByNodeId.get(self.id()));
        List<ListValueOption> allowed = new ArrayList<>(availableAbove);
        List<ListValueOption> included = List.of();
        List<ExcludedValue> excluded = new ArrayList<>();
        if (own.isPresent()) {
            Set<String> ownKeys = keysByNode.get(self.id());
            allowed.removeIf(value -> !ownKeys.contains(value.key()));
            included = values.stream()
                    .filter(value -> ownKeys.contains(value.key()))
                    .toList();
            Set<String> aboveKeys =
                    availableAbove.stream().map(ListValueOption::key).collect(Collectors.toSet());
            for (ListValueOption value : included) {
                if (!aboveKeys.contains(value.key())) {
                    excluded.add(new ExcludedValue(value, nearestExcluding(chain, value.key(), keysByNode)));
                }
            }
        }
        return new AllowedValues(allowed, availableAbove, own, included, excluded, involved);
    }

    /** Whether filters apply but no value is allowed (FR-011). */
    public boolean noneAllowed() {
        return allowed.isEmpty() && !involved.isEmpty();
    }

    /**
     * The comparison keys of the values allowed by the ancestors' filters (FR-009). Build it once and check many
     * values against it.
     */
    public Set<String> availableAboveKeys() {
        return availableAbove.stream().map(ListValueOption::key).collect(Collectors.toSet());
    }

    /**
     * The nearest ancestor whose filter does not include the value.
     *
     * @throws IllegalStateException if every ancestor allows it
     */
    public static NodeReference excludedBy(
            List<TaxonomyNode> chain, String valueKey, Map<String, ValueFilter> filtersByNodeId) {
        return nearestExcluding(chain, valueKey, keysByNode(chain, filtersByNodeId));
    }

    private static NodeReference nearestExcluding(
            List<TaxonomyNode> chain, String valueKey, Map<String, Set<String>> keysByNode) {
        for (int depth = chain.size() - 2; depth >= 0; depth--) {
            Set<String> keys = keysByNode.get(chain.get(depth).id());
            if (keys != null && !keys.contains(valueKey)) {
                return chain.get(depth).reference();
            }
        }
        throw new IllegalStateException("No ancestor excludes " + valueKey);
    }

    private static Map<String, Set<String>> keysByNode(
            List<TaxonomyNode> chain, Map<String, ValueFilter> filtersByNodeId) {
        Map<String, Set<String>> keys = new java.util.HashMap<>();
        for (TaxonomyNode node : chain) {
            ValueFilter filter = filtersByNodeId.get(node.id());
            if (filter != null) {
                keys.put(node.id(), filter.valueKeys());
            }
        }
        return keys;
    }
}
