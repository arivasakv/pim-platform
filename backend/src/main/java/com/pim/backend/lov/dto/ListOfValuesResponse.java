package com.pim.backend.lov.dto;

import com.pim.backend.lov.model.ListDetails;
import java.time.Instant;
import java.util.List;

/**
 * One list with its values (in the order added, equivalences included even when the column is off) and its linked
 * attributes (contract {@code ListOfValues}).
 */
public record ListOfValuesResponse(
        String systemId,
        String name,
        boolean equivalencesEnabled,
        int valueCount,
        List<ListValueResponse> values,
        List<LinkedAttributeResponse> attributes,
        Instant createdAt) {

    public static ListOfValuesResponse from(ListDetails details) {
        return new ListOfValuesResponse(
                details.list().systemId(),
                details.list().name().value(),
                details.list().equivalencesEnabled(),
                details.list().values().size(),
                details.list().values().stream().map(ListValueResponse::from).toList(),
                details.attributes().stream().map(LinkedAttributeResponse::from).toList(),
                details.list().createdAt());
    }
}
