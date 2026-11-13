package com.pim.backend.taxonomy.model;

import java.util.Objects;
import java.util.Optional;

/**
 * One attribute that applies to a node, before its name and type are looked up (spec FR-013, FR-014).
 *
 * @param attributeId the attribute's system ID, upper-cased
 * @param assignment {@code DIRECT} if assigned to the node, else {@code INHERITED}
 * @param origin the node itself for {@code DIRECT}; the nearest ancestor with a direct assignment otherwise
 * @param alsoInheritedFrom for {@code DIRECT} only: the nearest ancestor that also has it directly
 */
public record EffectiveAttribute(
        String attributeId,
        AssignmentType assignment,
        NodeReference origin,
        Optional<NodeReference> alsoInheritedFrom) {

    public EffectiveAttribute {
        Objects.requireNonNull(attributeId, "attributeId");
        Objects.requireNonNull(assignment, "assignment");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(alsoInheritedFrom, "alsoInheritedFrom");
    }
}
