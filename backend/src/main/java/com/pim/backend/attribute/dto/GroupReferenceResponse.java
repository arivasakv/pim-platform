package com.pim.backend.attribute.dto;

import com.pim.backend.attribute.model.GroupReference;

/** A group that contains the attribute (feature 007 contract {@code GroupReference}). */
public record GroupReferenceResponse(String id, String name) {

    public static GroupReferenceResponse from(GroupReference group) {
        return new GroupReferenceResponse(group.id(), group.name());
    }
}
