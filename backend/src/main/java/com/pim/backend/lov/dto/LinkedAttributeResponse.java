package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.LinkedAttribute;

/** An attribute that uses the list (contract {@code LinkedAttribute}). */
public record LinkedAttributeResponse(String systemId, String displayName, String selectionMode) {

    public static LinkedAttributeResponse from(LinkedAttribute attribute) {
        return new LinkedAttributeResponse(
                attribute.systemId(),
                attribute.displayName(),
                attribute.selectionMode() == null
                        ? null
                        : attribute.selectionMode().name());
    }
}
