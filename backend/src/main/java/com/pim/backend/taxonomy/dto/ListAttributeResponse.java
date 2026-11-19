package com.pim.backend.taxonomy.dto;

import com.pim.backend.taxonomy.model.AllowedValues;
import com.pim.backend.taxonomy.model.ListAttributeSummary;
import java.util.List;

/** A list-of-values attribute at a node with its allowed values count (contract {@code ListAttribute}, feature 012). */
public record ListAttributeResponse(
        String systemId,
        String displayName,
        String listOfValuesId,
        String listName,
        String selectionMode,
        int totalValues,
        int allowedCount,
        boolean hasOwnFilter,
        List<FilterOnPathResponse> filters) {

    public static ListAttributeResponse from(ListAttributeSummary summary) {
        return new ListAttributeResponse(
                summary.systemId(),
                summary.displayName(),
                summary.listOfValuesId(),
                summary.listName(),
                summary.selectionMode(),
                summary.totalValues(),
                summary.allowedCount(),
                summary.hasOwnFilter(),
                summary.filters().stream().map(FilterOnPathResponse::from).toList());
    }

    /** A filter on the node's chain (contract {@code FilterOnPath}). */
    public record FilterOnPathResponse(NodeReferenceResponse node, int includedCount) {

        static FilterOnPathResponse from(AllowedValues.FilterOnPath filter) {
            return new FilterOnPathResponse(NodeReferenceResponse.from(filter.node()), filter.includedCount());
        }
    }
}
