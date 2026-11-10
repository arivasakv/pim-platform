package com.pim.backend.attributegroup.model;

import java.util.List;
import java.util.Objects;

/**
 * The result of adding attributes to a group (plan decision D-6; FR-008).
 *
 * @param details the group after the change
 * @param added system IDs newly added, in request order
 * @param alreadyInGroup system IDs that were already members and were not added again
 */
public record AddAttributesOutcome(GroupDetails details, List<String> added, List<String> alreadyInGroup) {

    public AddAttributesOutcome {
        Objects.requireNonNull(details, "details");
        added = List.copyOf(added);
        alreadyInGroup = List.copyOf(alreadyInGroup);
    }
}
