package com.pim.backend.attributegroup.repository;

import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.GroupName;
import java.util.List;
import java.util.Optional;

/**
 * Stores attribute groups and their members. Holds no business rules (constitution XII); {@code
 * AttributeGroupService} checks names and members before saving.
 */
public interface AttributeGroupRepository {

    Optional<AttributeGroup> findById(String id);

    /** Every group, sorted by name ignoring letter case. */
    List<AttributeGroup> findAll();

    /**
     * Whether a group other than {@code excludedId} has this name, ignoring letter case.
     *
     * @param excludedId the group being renamed, or {@code null} when creating
     */
    boolean existsWithName(GroupName name, String excludedId);

    /**
     * Saves a new group.
     *
     * @throws IllegalStateException if its identifier or name is already stored
     */
    void insert(AttributeGroup group);

    /**
     * Replaces a stored group (same identifier).
     *
     * @throws IllegalStateException if another group has its name
     */
    void update(AttributeGroup group);

    void delete(String id);
}
