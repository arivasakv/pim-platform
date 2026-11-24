package com.pim.backend.attribute.model;

import java.util.List;
import java.util.Optional;

/**
 * What unlinking an attribute's list would affect; nothing is changed (feature 014 FR-009, data-model "Unlink
 * review").
 *
 * @param attributeId the attribute's system ID
 * @param attributeName the attribute's display name
 * @param listOfValuesId the list that would be detached
 * @param listOfValuesName its name; empty if the list no longer exists
 * @param filteredNodes the taxonomy nodes whose value filters for the attribute would be deleted, sorted by name
 * @param reviewToken identifies the attribute and its filters as reviewed; sent back to unlink (research R-02)
 */
public record ListUnlinkReview(
        String attributeId,
        String attributeName,
        String listOfValuesId,
        Optional<String> listOfValuesName,
        List<TaxonomyNodeReference> filteredNodes,
        String reviewToken) {

    public ListUnlinkReview {
        filteredNodes = List.copyOf(filteredNodes);
    }
}
