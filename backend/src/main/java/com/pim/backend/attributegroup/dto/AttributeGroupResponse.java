package com.pim.backend.attributegroup.dto;

import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.GroupDetails;
import java.time.Instant;
import java.util.List;

/** One group with its members in the order they were added (contract {@code AttributeGroup}). */
public record AttributeGroupResponse(
        String id,
        String name,
        String description,
        int attributeCount,
        List<GroupMemberResponse> attributes,
        Instant createdAt) {

    public static AttributeGroupResponse from(GroupDetails details) {
        AttributeGroup group = details.group();
        return new AttributeGroupResponse(
                group.id(),
                group.name().value(),
                group.description().orElse(null),
                group.attributeIds().size(),
                details.members().stream().map(GroupMemberResponse::from).toList(),
                group.createdAt());
    }
}
