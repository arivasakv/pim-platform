package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.AllowedValues;
import com.pim.backend.taxonomy.model.ListValueOption;
import com.pim.backend.taxonomy.model.ValueFilterDetails;
import java.util.List;

/** A list attribute's values at a node (contract {@code ValueFilterDetail}, feature 012). */
public record ValueFilterResponse(
        NodeReferenceResponse node,
        ListAttributeResponse attribute,
        int totalValues,
        List<ListValueResponse> availableValues,
        List<String> includedValueIds,
        List<ExcludedValueResponse> excludedAboveValues,
        List<ListValueResponse> allowedValues,
        List<ListAttributeResponse.FilterOnPathResponse> filters) {

    public static ValueFilterResponse from(ValueFilterDetails details) {
        AllowedValues values = details.values();
        ListAttributeResponse attribute = ListAttributeResponse.from(details.attribute());
        return new ValueFilterResponse(
                NodeReferenceResponse.from(details.node()),
                attribute,
                details.attribute().totalValues(),
                values.availableAbove().stream().map(ListValueResponse::from).toList(),
                values.ownFilter().isPresent()
                        ? values.includedValues().stream()
                                .map(ListValueOption::id)
                                .toList()
                        : null,
                values.excludedAbove().stream().map(ExcludedValueResponse::from).toList(),
                values.allowed().stream().map(ListValueResponse::from).toList(),
                attribute.filters());
    }

    /** One list value (contract {@code ListValue}). */
    public record ListValueResponse(String id, String label) {

        static ListValueResponse from(ListValueOption value) {
            return new ListValueResponse(value.id(), value.label());
        }
    }

    /** A value of the node's own filter that a filter above no longer allows (contract {@code ExcludedValue}). */
    public record ExcludedValueResponse(String id, String label, NodeReferenceResponse excludedBy) {

        static ExcludedValueResponse from(AllowedValues.ExcludedValue excluded) {
            return new ExcludedValueResponse(
                    excluded.value().id(), excluded.value().label(), NodeReferenceResponse.from(excluded.excludedBy()));
        }
    }
}
