package com.pim.backend.attribute.model;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The attribute belongs to groups, so it cannot be deleted until it is removed from them (feature 007 FR-014; HTTP
 * 409). Nothing changed.
 */
public class AttributeInGroupsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<GroupReference> groups;

    public AttributeInGroupsException(List<GroupReference> groups) {
        super(message(groups));
        this.groups = List.copyOf(groups);
    }

    /** The groups that contain the attribute. */
    public List<GroupReference> groups() {
        return groups;
    }

    private static String message(List<GroupReference> groups) {
        String count = groups.size() == 1 ? "1 grupo" : groups.size() + " grupos";
        String names = groups.stream().map(GroupReference::name).collect(Collectors.joining(", "));
        return AttributeErrorCode.ATTRIBUTE_IN_GROUPS.message(count, names);
    }
}
