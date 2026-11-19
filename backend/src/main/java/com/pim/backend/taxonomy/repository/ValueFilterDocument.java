package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.ValueFilter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A list-value filter as stored in the collection {@value #COLLECTION} (feature 012 data-model; ADR 0003, constitution
 * 2.6.0). Package-private: Spring Data types never leave the repository package (constitution XII).
 *
 * @param id {@code NODE:ATTRIBUTE}, both upper-cased
 */
@Document(ValueFilterDocument.COLLECTION)
record ValueFilterDocument(
        @Id String id, String nodeId, String attributeId, List<String> valueIds, Instant createdAt, Instant updatedAt) {

    static final String COLLECTION = "taxonomy_value_filters";
    static final String NODE_ID = "nodeId";
    static final String ATTRIBUTE_ID = "attributeId";
    static final String UNIQUE_INDEX = "taxonomy_value_filter_unique";

    static ValueFilterDocument from(ValueFilter filter) {
        return new ValueFilterDocument(
                filter.id(),
                filter.nodeId(),
                filter.attributeId(),
                filter.valueIds(),
                filter.createdAt(),
                filter.updatedAt().orElse(null));
    }

    ValueFilter toFilter() {
        return new ValueFilter(nodeId, attributeId, valueIds, createdAt, Optional.ofNullable(updatedAt));
    }
}
