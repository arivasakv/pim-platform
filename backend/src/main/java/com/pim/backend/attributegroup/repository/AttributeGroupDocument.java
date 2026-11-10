package com.pim.backend.attributegroup.repository;

import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.GroupName;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An attribute group as stored in the collection {@value #COLLECTION} (data-model.md; ADR 0003). Package-private:
 * Spring Data types never leave the repository package (constitution XII).
 *
 * @param id the group's UUID
 * @param nameKey the name lower-cased ({@code Locale.ROOT}); unique index {@value #NAME_INDEX}
 * @param attributeIds member system IDs in the order they were added
 */
@Document(AttributeGroupDocument.COLLECTION)
record AttributeGroupDocument(
        @Id String id, String name, String nameKey, String description, List<String> attributeIds, Instant createdAt) {

    static final String COLLECTION = "attribute_groups";
    static final String NAME_KEY = "nameKey";
    static final String NAME_INDEX = "attribute_group_name_unique";

    static AttributeGroupDocument from(AttributeGroup group) {
        return new AttributeGroupDocument(
                group.id(),
                group.name().value(),
                group.name().comparisonKey(),
                group.description().orElse(null),
                group.attributeIds(),
                group.createdAt());
    }

    AttributeGroup toGroup() {
        return new AttributeGroup(
                id,
                GroupName.of(name),
                Optional.ofNullable(description),
                attributeIds == null ? List.of() : attributeIds,
                createdAt);
    }
}
