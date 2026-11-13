package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.NodeName;
import com.pim.backend.taxonomy.model.TaxonomyNode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A taxonomy node as stored in the collection {@value #COLLECTION} (data-model.md; ADR 0003). Package-private:
 * Spring Data types never leave the repository package (constitution XII).
 *
 * @param id the system ID upper-cased
 * @param nameKey the name lower-cased ({@code Locale.ROOT}); unique with {@code parentId} ({@value #NAME_INDEX})
 * @param parentId the parent's {@code id}; {@code null} at the top level
 * @param attributeIds direct attribute system IDs, upper-cased, in assignment order
 */
@Document(TaxonomyNodeDocument.COLLECTION)
record TaxonomyNodeDocument(
        @Id String id,
        String systemId,
        String name,
        String nameKey,
        String parentId,
        String description,
        List<String> attributeIds,
        Instant createdAt,
        Instant updatedAt) {

    static final String COLLECTION = "taxonomy_nodes";
    static final String NAME_KEY = "nameKey";
    static final String PARENT_ID = "parentId";
    static final String ATTRIBUTE_IDS = "attributeIds";
    static final String NAME_INDEX = "taxonomy_node_sibling_name_unique";
    static final String ATTRIBUTE_INDEX = "taxonomy_node_attribute_ids";

    static TaxonomyNodeDocument from(TaxonomyNode node) {
        return new TaxonomyNodeDocument(
                node.id(),
                node.systemId(),
                node.name().value(),
                node.name().comparisonKey(),
                node.parentId().orElse(null),
                node.description().orElse(null),
                node.attributeIds(),
                node.createdAt(),
                node.updatedAt().orElse(null));
    }

    TaxonomyNode toNode() {
        return new TaxonomyNode(
                systemId,
                NodeName.of(name),
                Optional.ofNullable(description),
                Optional.ofNullable(parentId),
                attributeIds == null ? List.of() : attributeIds,
                createdAt,
                Optional.ofNullable(updatedAt));
    }
}
