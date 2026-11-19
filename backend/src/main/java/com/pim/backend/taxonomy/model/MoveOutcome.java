package com.pim.backend.taxonomy.model;

import java.util.Objects;

/**
 * The result of moving a node (feature 011 FR-001, FR-014).
 *
 * @param details the moved node, with its new level, path and effective attributes
 * @param movedNodeCount the node plus all its descendants; at least 1
 */
public record MoveOutcome(NodeDetails details, int movedNodeCount) {

    public MoveOutcome {
        Objects.requireNonNull(details, "details");
        if (movedNodeCount < 1) {
            throw new IllegalArgumentException("movedNodeCount must be at least 1: " + movedNodeCount);
        }
    }
}
