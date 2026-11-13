package com.pim.backend.taxonomy.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Inheritance rules of the product hierarchy (spec FR-012 to FR-016; data-model.md "EffectiveAttributes rules").
 * Pure: it reads nodes and returns values, so every read reflects the current tree (FR-015).
 */
public final class EffectiveAttributes {

    private EffectiveAttributes() {}

    /**
     * The node's ancestors from the top level down, then the node itself. A missing parent ends the chain (it cannot
     * happen while nodes with children are never deleted), and a repeated node stops it, so a damaged store never
     * loops.
     *
     * @param nodesById every node keyed by {@link TaxonomyNode#id()}
     */
    public static List<TaxonomyNode> chain(TaxonomyNode node, Map<String, TaxonomyNode> nodesById) {
        List<TaxonomyNode> upward = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        TaxonomyNode current = node;
        while (current != null && seen.add(current.id())) {
            upward.add(current);
            current = current.parentId().map(nodesById::get).orElse(null);
        }
        List<TaxonomyNode> downward = new ArrayList<>(upward);
        java.util.Collections.reverse(downward);
        return List.copyOf(downward);
    }

    /**
     * Every attribute that applies to the node, each once (FR-013): inherited ones first, grouped by origin from the
     * top level down, then direct ones; within one origin, in assignment order (FR-016). The origin of an inherited
     * attribute is the nearest ancestor that has it directly (FR-014).
     *
     * @param nodesById every node keyed by {@link TaxonomyNode#id()}
     */
    public static List<EffectiveAttribute> of(TaxonomyNode node, Map<String, TaxonomyNode> nodesById) {
        List<TaxonomyNode> chain = chain(node, nodesById);
        int self = chain.size() - 1;
        // For each attribute: the chain positions that have it directly, from the top down.
        Map<String, List<Integer>> holders = new LinkedHashMap<>();
        for (int depth = 0; depth < chain.size(); depth++) {
            for (String attributeId : chain.get(depth).attributeIds()) {
                holders.computeIfAbsent(attributeId, key -> new ArrayList<>()).add(depth);
            }
        }
        Map<String, Integer> originDepth = new HashMap<>();
        List<EffectiveAttribute> result = new ArrayList<>();
        holders.forEach((attributeId, depths) -> {
            int origin = depths.get(depths.size() - 1);
            originDepth.put(attributeId, origin);
            boolean direct = origin == self;
            Optional<NodeReference> also = direct && depths.size() > 1
                    ? Optional.of(chain.get(depths.get(depths.size() - 2)).reference())
                    : Optional.empty();
            result.add(new EffectiveAttribute(
                    attributeId,
                    direct ? AssignmentType.DIRECT : AssignmentType.INHERITED,
                    chain.get(origin).reference(),
                    also));
        });
        result.sort(Comparator.comparingInt((EffectiveAttribute entry) -> originDepth.get(entry.attributeId()))
                .thenComparingInt(entry -> chain.get(originDepth.get(entry.attributeId()))
                        .attributeIds()
                        .indexOf(entry.attributeId())));
        return List.copyOf(result);
    }
}
