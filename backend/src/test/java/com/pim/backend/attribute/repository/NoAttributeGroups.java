package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.GroupReference;
import java.util.List;
import java.util.Map;

/**
 * Test fake: an {@link AttributeGroupDirectory} with no groups, for unit tests that build {@code AttributeService} by
 * hand. Production uses the attribute group feature's adapter (feature 007 plan decision D-2).
 */
public class NoAttributeGroups implements AttributeGroupDirectory {

    @Override
    public Map<String, List<GroupReference>> groupsByAttribute() {
        return Map.of();
    }

    @Override
    public List<GroupReference> runIfInNoGroup(String attributeId, Runnable action) {
        action.run();
        return List.of();
    }
}
