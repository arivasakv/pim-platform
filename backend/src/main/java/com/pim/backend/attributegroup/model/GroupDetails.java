package com.pim.backend.attributegroup.model;

import java.util.List;
import java.util.Objects;

/**
 * A group with its members' details, in the group's member order (FR-012).
 *
 * @param group the group
 * @param members one entry per {@code group.attributeIds()}, same order
 */
public record GroupDetails(AttributeGroup group, List<GroupMember> members) {

    public GroupDetails {
        Objects.requireNonNull(group, "group");
        members = List.copyOf(members);
    }
}
