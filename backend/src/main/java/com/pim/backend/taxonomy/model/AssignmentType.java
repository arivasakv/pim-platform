package com.pim.backend.taxonomy.model;

/** How an attribute applies to a node (spec FR-014). */
public enum AssignmentType {
    /** Assigned to the node itself. */
    DIRECT,
    /** Assigned to an ancestor and received through inheritance. */
    INHERITED
}
