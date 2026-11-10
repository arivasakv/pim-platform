package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link ObjectTypeConfigurationRepository} stored in MongoDB, collection {@code object_type_configurations}
 * (constitution IX; ADR 0003). The next value of each sequence survives restarts. {@code IdentifierService} calls it
 * under one lock (research R-10).
 */
@Repository
public class MongoObjectTypeConfigurationRepository implements ObjectTypeConfigurationRepository {

    private final MongoTemplate mongoTemplate;

    MongoObjectTypeConfigurationRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<ObjectTypeConfiguration> findAll() {
        return mongoTemplate.findAll(ObjectTypeConfigurationDocument.class).stream()
                .sorted(Comparator.comparing(ObjectTypeConfigurationDocument::objectType))
                .map(ObjectTypeConfigurationDocument::toConfiguration)
                .toList();
    }

    @Override
    public ObjectTypeConfiguration find(ObjectType objectType) {
        ObjectTypeConfigurationDocument document =
                mongoTemplate.findById(objectType.name(), ObjectTypeConfigurationDocument.class);
        if (document == null) {
            throw new IllegalStateException("Object type not configured: " + objectType);
        }
        return document.toConfiguration();
    }

    @Override
    public boolean contains(ObjectType objectType) {
        return mongoTemplate.findById(objectType.name(), ObjectTypeConfigurationDocument.class) != null;
    }

    @Override
    public void save(ObjectTypeConfiguration configuration) {
        mongoTemplate.save(ObjectTypeConfigurationDocument.from(configuration));
    }
}
