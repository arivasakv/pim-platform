package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.GroupReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Test fake: an {@link AttributeGroupDirectory} whose memberships a test sets with {@link #put}. */
public class FakeAttributeGroups implements AttributeGroupDirectory {

    private final Map<String, List<GroupReference>> byAttribute = new HashMap<>();

    /** Puts the attribute in these groups (in this order). */
    public void put(String attributeId, GroupReference... groups) {
        byAttribute.put(attributeId.toUpperCase(Locale.ROOT), List.of(groups));
    }

    @Override
    public synchronized Map<String, List<GroupReference>> groupsByAttribute() {
        return Map.copyOf(byAttribute);
    }

    @Override
    public synchronized List<GroupReference> runIfInNoGroup(String attributeId, Runnable action) {
        List<GroupReference> groups =
                new ArrayList<>(byAttribute.getOrDefault(attributeId.toUpperCase(Locale.ROOT), List.of()));
        if (groups.isEmpty()) {
            action.run();
        }
        return List.copyOf(groups);
    }
}
