package com.pim.backend.attributegroup.dto;

import com.pim.backend.attributegroup.model.GroupMember;

/** One member attribute of a group (contract {@code GroupMember}). */
public record GroupMemberResponse(String systemId, String displayName, String dataType) {

    public static GroupMemberResponse from(GroupMember member) {
        return new GroupMemberResponse(member.systemId(), member.displayName(), member.dataType());
    }
}
