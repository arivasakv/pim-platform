package com.pim.backend.attribute.dto;

import com.pim.backend.attribute.model.ListUnlinkReview;
import java.util.List;

/** What unlinking the attribute's list would affect (feature 014 contract {@code ListUnlinkReview}). */
public record ListUnlinkReviewResponse(
        String attributeId,
        String attributeName,
        String listOfValuesId,
        String listOfValuesName,
        List<NodeReferenceResponse> filteredNodes,
        String reviewToken) {

    public static ListUnlinkReviewResponse from(ListUnlinkReview review) {
        return new ListUnlinkReviewResponse(
                review.attributeId(),
                review.attributeName(),
                review.listOfValuesId(),
                review.listOfValuesName().orElse(null),
                review.filteredNodes().stream()
                        .map(node -> new NodeReferenceResponse(node.systemId(), node.name()))
                        .toList(),
                review.reviewToken());
    }
}
