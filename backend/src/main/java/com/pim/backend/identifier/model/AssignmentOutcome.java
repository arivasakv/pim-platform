package com.pim.backend.identifier.model;

/**
 * Result of an assignment request: a new ID was registered, or the ID already belongs to the same object
 * (FR-009a; plan decision D-10).
 */
public enum AssignmentOutcome {
    ASSIGNED,
    SAME_OBJECT
}
