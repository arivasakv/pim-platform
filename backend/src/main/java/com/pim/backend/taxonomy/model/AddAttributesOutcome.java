package com.pim.backend.taxonomy.model;

import java.util.List;
import java.util.Objects;

/**
 * The result of assigning attributes to a node (spec FR-009).
 *
 * @param details the node after the change
 * @param added system IDs newly assigned, in request order
 * @param alreadyAssigned system IDs that were already direct and were not added again
 */
public record AddAttributesOutcome(NodeDetails details, List<String> added, List<String> alreadyAssigned) {

    public AddAttributesOutcome {
        Objects.requireNonNull(details, "details");
        added = List.copyOf(added);
        alreadyAssigned = List.copyOf(alreadyAssigned);
    }
}
