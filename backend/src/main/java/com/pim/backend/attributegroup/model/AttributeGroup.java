package com.pim.backend.attributegroup.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A flat, named set of attributes (data-model.md "AttributeGroup"; FR-001, FR-004). It holds the system IDs of its
 * members, never the attributes themselves, so group changes never touch an attribute (FR-010). There is no parent
 * group (FR-004).
 *
 * @param id generated at creation; never changes, not even on rename (FR-016)
 * @param name unique ignoring letter case (FR-003)
 * @param description optional, up to 500 characters
 * @param attributeIds member system IDs in the order they were added, each at most once ignoring case (FR-008)
 * @param createdAt when the group was created
 */
public record AttributeGroup(
        String id, GroupName name, Optional<String> description, List<String> attributeIds, Instant createdAt) {

    /** Longest accepted description. */
    public static final int MAX_DESCRIPTION_LENGTH = 500;

    public AttributeGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(createdAt, "createdAt");
        attributeIds = List.copyOf(attributeIds);
        Set<String> seen = new HashSet<>();
        for (String attributeId : attributeIds) {
            if (!seen.add(key(attributeId))) {
                throw new IllegalArgumentException("Attribute listed twice in group " + id + ": " + attributeId);
            }
        }
    }

    /**
     * A new group with no attributes.
     *
     * @throws InvalidGroupException {@code INVALID_GROUP_DESCRIPTION} for a description longer than 500 characters
     */
    public static AttributeGroup create(String id, GroupName name, String description, Instant createdAt) {
        return new AttributeGroup(id, name, normalizedDescription(description), List.of(), createdAt);
    }

    /**
     * The description trimmed; blank means absent.
     *
     * @throws InvalidGroupException {@code INVALID_GROUP_DESCRIPTION} when longer than 500 characters
     */
    public static Optional<String> normalizedDescription(String description) {
        if (description == null || description.isBlank()) {
            return Optional.empty();
        }
        String trimmed = description.strip();
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidGroupException("description", AttributeGroupErrorCode.INVALID_GROUP_DESCRIPTION);
        }
        return Optional.of(trimmed);
    }

    /** A copy with these members; the identifier, name and description stay. */
    public AttributeGroup withAttributes(List<String> newAttributeIds) {
        return new AttributeGroup(id, name, description, newAttributeIds, createdAt);
    }

    /**
     * A copy with another name and description; the identifier and members stay (FR-005).
     *
     * @throws InvalidGroupException {@code INVALID_GROUP_DESCRIPTION} for a description longer than 500 characters
     */
    public AttributeGroup renamed(GroupName newName, String newDescription) {
        return new AttributeGroup(id, newName, normalizedDescription(newDescription), attributeIds, createdAt);
    }

    /** Whether the attribute is a member, comparing system IDs ignoring letter case. */
    public boolean contains(String attributeId) {
        String wanted = key(attributeId);
        return attributeIds.stream().anyMatch(member -> key(member).equals(wanted));
    }

    private static String key(String attributeId) {
        return attributeId.toUpperCase(Locale.ROOT);
    }
}
