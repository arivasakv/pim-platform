package com.pim.backend.common;

import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Puts the attribute administration collections of the test database back to a fresh install (ADR 0003): no system
 * IDs, attributes, groups, lists of values or list change records, and the default object-type configurations. Integration tests call it before each
 * test because data now outlives a Spring context.
 *
 * <p>The collections are emptied, never dropped: dropping would also remove their unique indexes, which are created
 * once when the application starts.
 */
public final class AttributeAdministrationTestData {

    private static final List<String> COLLECTIONS = List.of(
            "system_ids",
            "object_type_configurations",
            "attributes",
            "attribute_groups",
            "lists_of_values",
            "lov_changes");

    private AttributeAdministrationTestData() {}

    public static void reset(MongoTemplate mongoTemplate, DefaultObjectTypeConfigurations defaults) {
        COLLECTIONS.forEach(collection -> mongoTemplate.remove(new Query(), collection));
        defaults.seedMissing();
    }
}
