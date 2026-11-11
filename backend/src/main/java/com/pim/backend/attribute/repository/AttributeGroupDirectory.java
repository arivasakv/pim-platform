package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.GroupReference;
import java.util.List;
import java.util.Map;

/**
 * The attribute groups that contain attributes (feature 007 research R-02). Implemented by the attribute group
 * feature (feature 005, {@code AttributeGroupDirectoryAdapter}) on top of its own service and lock, so this domain
 * never reads groups directly (constitution III).
 *
 * <p>Lock order is group → attribute (research R-06): callers must not hold the attribute lock while calling these
 * methods.
 */
public interface AttributeGroupDirectory {

    /** Every membership: the groups of each attribute, keyed by the attribute's system ID upper-cased. */
    Map<String, List<GroupReference>> groupsByAttribute();

    /**
     * Runs {@code action} only if no group contains the attribute (compared ignoring letter case), while holding the
     * group lock, so no group can add the attribute in between.
     *
     * @return the groups that contain the attribute, sorted by name ignoring letter case; empty if the action ran
     */
    List<GroupReference> runIfInNoGroup(String attributeId, Runnable action);
}
