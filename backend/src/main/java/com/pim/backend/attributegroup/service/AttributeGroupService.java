package com.pim.backend.attributegroup.service;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.attributegroup.model.AddAttributesOutcome;
import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.AttributeNotInGroupException;
import com.pim.backend.attributegroup.model.GroupChangeNotPermittedException;
import com.pim.backend.attributegroup.model.GroupDetails;
import com.pim.backend.attributegroup.model.GroupMember;
import com.pim.backend.attributegroup.model.GroupName;
import com.pim.backend.attributegroup.model.GroupNameInUseException;
import com.pim.backend.attributegroup.model.GroupNotEmptyException;
import com.pim.backend.attributegroup.model.GroupNotFoundException;
import com.pim.backend.attributegroup.model.UnknownAttributeException;
import com.pim.backend.attributegroup.repository.AttributeGroupRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Manages attribute groups and their members (spec FR-001 to FR-016; data-model.md "Operation flows"). Attributes
 * come from feature 004 and are used only through {@link AttributeService} (constitution III); group changes never
 * change an attribute (FR-010). Every change runs under one lock (research R-12).
 */
@Service
public class AttributeGroupService {

    private final AttributeGroupRepository groups;
    private final AttributeService attributeService;
    private final Clock clock;
    private final Object lock = new Object();

    public AttributeGroupService(AttributeGroupRepository groups, AttributeService attributeService, Clock clock) {
        this.groups = groups;
        this.attributeService = attributeService;
        this.clock = clock;
    }

    /**
     * Creates an empty group with a generated identifier (FR-001, FR-016).
     *
     * @throws GroupChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws com.pim.backend.attributegroup.model.InvalidGroupException for an invalid name or description
     * @throws GroupNameInUseException when another group has the name, ignoring letter case
     */
    public GroupDetails create(String name, String description, AttributePermissions permissions) {
        checkPermission(permissions);
        GroupName groupName = GroupName.of(name);
        AttributeGroup group =
                AttributeGroup.create(UUID.randomUUID().toString(), groupName, description, clock.instant());
        synchronized (lock) {
            if (groups.existsWithName(groupName, null)) {
                throw new GroupNameInUseException(groupName);
            }
            groups.insert(group);
            return new GroupDetails(group, List.of());
        }
    }

    /** Every group, sorted by name ignoring letter case (FR-012). */
    public List<AttributeGroup> findAll() {
        synchronized (lock) {
            return groups.findAll();
        }
    }

    /** The groups containing this attribute, compared ignoring letter case; none for an unknown attribute. */
    public List<AttributeGroup> findAllContaining(String attributeId) {
        synchronized (lock) {
            return groups.findAll().stream()
                    .filter(group -> group.contains(attributeId))
                    .toList();
        }
    }

    /**
     * Runs {@code action} only if no group contains the attribute (compared ignoring letter case), while holding the
     * group lock, so no membership can be added in between (feature 007 FR-014, FR-017; research R-05). The action may
     * take the attribute lock: the order group → attribute is the one {@link #addAttributes} already uses.
     *
     * @return the groups containing the attribute, sorted by name ignoring letter case; empty if the action ran
     */
    public List<AttributeGroup> runIfInNoGroup(String attributeId, Runnable action) {
        synchronized (lock) {
            List<AttributeGroup> containing = groups.findAll().stream()
                    .filter(group -> group.contains(attributeId))
                    .toList();
            if (containing.isEmpty()) {
                action.run();
            }
            return containing;
        }
    }

    /**
     * One group with its members' details (FR-012).
     *
     * @throws GroupNotFoundException if no group has the identifier
     */
    public GroupDetails find(String groupId) {
        synchronized (lock) {
            return details(existing(groupId));
        }
    }

    /**
     * Renames the group and replaces its description; its identifier and members stay (FR-005).
     *
     * @throws GroupChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws GroupNotFoundException if no group has the identifier
     * @throws GroupNameInUseException when another group has the name, ignoring letter case
     */
    public GroupDetails update(String groupId, String name, String description, AttributePermissions permissions) {
        checkPermission(permissions);
        GroupName groupName = GroupName.of(name);
        AttributeGroup.normalizedDescription(description);
        synchronized (lock) {
            AttributeGroup group = existing(groupId);
            if (groups.existsWithName(groupName, groupId)) {
                throw new GroupNameInUseException(groupName);
            }
            AttributeGroup renamed = group.renamed(groupName, description);
            groups.update(renamed);
            return details(renamed);
        }
    }

    /**
     * Deletes an empty group (FR-005).
     *
     * @throws GroupChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws GroupNotFoundException if no group has the identifier
     * @throws GroupNotEmptyException if the group still has attributes; nothing changes
     */
    public void delete(String groupId, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            AttributeGroup group = existing(groupId);
            if (!group.attributeIds().isEmpty()) {
                throw new GroupNotEmptyException(group);
            }
            groups.delete(groupId);
        }
    }

    /**
     * Adds existing attributes, all or nothing (FR-006, FR-008; plan decision D-6). Attributes already in the group
     * are reported, not added again. Members are stored with the attribute's own system ID.
     *
     * @throws GroupChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws GroupNotFoundException if no group has the identifier
     * @throws UnknownAttributeException naming the first unknown attribute; nothing is added
     */
    public AddAttributesOutcome addAttributes(
            String groupId, List<String> attributeIds, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            AttributeGroup group = existing(groupId);
            List<Attribute> attributes =
                    attributeIds.stream().map(this::existingAttribute).toList();
            List<String> members = new ArrayList<>(group.attributeIds());
            List<String> added = new ArrayList<>();
            List<String> alreadyInGroup = new ArrayList<>();
            for (Attribute attribute : attributes) {
                // The attribute's own system ID, so letter case never differs between requests.
                String systemId = attribute.systemId();
                if (!members.contains(systemId)) {
                    members.add(systemId);
                    added.add(systemId);
                } else if (!added.contains(systemId) && !alreadyInGroup.contains(systemId)) {
                    alreadyInGroup.add(systemId);
                }
            }
            AttributeGroup changed = group.withAttributes(members);
            if (!added.isEmpty()) {
                groups.update(changed);
            }
            return new AddAttributesOutcome(details(changed), added, alreadyInGroup);
        }
    }

    /**
     * Removes an attribute from the group; the attribute stays in the system and in its other groups (FR-009).
     *
     * @throws GroupChangeNotPermittedException without {@code ATTRIBUTE_CREATE}
     * @throws GroupNotFoundException if no group has the identifier
     * @throws AttributeNotInGroupException if the attribute is not a member (plan decision D-7)
     */
    public void removeAttribute(String groupId, String attributeId, AttributePermissions permissions) {
        checkPermission(permissions);
        synchronized (lock) {
            AttributeGroup group = existing(groupId);
            if (!group.contains(attributeId)) {
                throw new AttributeNotInGroupException(attributeId);
            }
            List<String> members = group.attributeIds().stream()
                    .filter(member -> !member.equalsIgnoreCase(attributeId))
                    .toList();
            groups.update(group.withAttributes(members));
        }
    }

    private static void checkPermission(AttributePermissions permissions) {
        if (!permissions.allowsCreation()) {
            throw new GroupChangeNotPermittedException();
        }
    }

    private AttributeGroup existing(String groupId) {
        return groups.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));
    }

    private Attribute existingAttribute(String attributeId) {
        try {
            return attributeService.find(attributeId);
        } catch (AttributeNotFoundException exception) {
            throw new UnknownAttributeException(attributeId);
        }
    }

    private GroupDetails details(AttributeGroup group) {
        List<GroupMember> members = group.attributeIds().stream()
                .map(attributeService::find)
                .map(attribute -> new GroupMember(
                        attribute.systemId(),
                        attribute.displayName().value(),
                        attribute.dataType().name()))
                .toList();
        return new GroupDetails(group, members);
    }
}
