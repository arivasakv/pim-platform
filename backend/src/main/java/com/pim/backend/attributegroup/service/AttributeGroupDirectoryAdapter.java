package com.pim.backend.attributegroup.service;

import com.pim.backend.attribute.model.GroupReference;
import com.pim.backend.attribute.repository.AttributeGroupDirectory;
import com.pim.backend.attributegroup.model.AttributeGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Feature 004's view of the groups that contain attributes (feature 007 plan decision D-2, research R-02). It
 * delegates to {@link AttributeGroupService}, which holds the group lock. The service is looked up when first used
 * ({@link ObjectProvider}), because it depends on {@code AttributeService}, which depends on this adapter.
 */
@Component
public class AttributeGroupDirectoryAdapter implements AttributeGroupDirectory {

    private final ObjectProvider<AttributeGroupService> groupService;

    public AttributeGroupDirectoryAdapter(ObjectProvider<AttributeGroupService> groupService) {
        this.groupService = groupService;
    }

    @Override
    public Map<String, List<GroupReference>> groupsByAttribute() {
        Map<String, List<GroupReference>> byAttribute = new HashMap<>();
        // Groups arrive sorted by name, so each attribute's list is sorted too.
        for (AttributeGroup group : groupService.getObject().findAll()) {
            for (String attributeId : group.attributeIds()) {
                byAttribute
                        .computeIfAbsent(attributeId.toUpperCase(Locale.ROOT), key -> new ArrayList<>())
                        .add(reference(group));
            }
        }
        Map<String, List<GroupReference>> result = new HashMap<>();
        byAttribute.forEach((attributeId, groups) -> result.put(attributeId, List.copyOf(groups)));
        return Map.copyOf(result);
    }

    @Override
    public List<GroupReference> runIfInNoGroup(String attributeId, Runnable action) {
        return groupService.getObject().runIfInNoGroup(attributeId, action).stream()
                .map(AttributeGroupDirectoryAdapter::reference)
                .toList();
    }

    private static GroupReference reference(AttributeGroup group) {
        return new GroupReference(group.id(), group.name().value());
    }
}
