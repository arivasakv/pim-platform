package com.pim.backend.identifier.repository;

import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdRegistration;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * {@link SystemIdRegistry} stored in MongoDB, collection {@code system_ids} (constitution IX; ADR 0003). Registrations
 * survive restarts, so an ID is never given twice. {@code IdentifierService} still calls it under one lock (research
 * R-10); the {@code _id} (the ID upper-cased) also refuses duplicates. If MongoDB cannot be reached, Spring's {@code
 * DataAccessResourceFailureException} propagates (503).
 */
@Repository
public class MongoSystemIdRegistry implements SystemIdRegistry {

    private final MongoTemplate mongoTemplate;

    MongoSystemIdRegistry(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<SystemIdRegistration> find(SystemId systemId) {
        return Optional.ofNullable(mongoTemplate.findById(SystemIdDocument.key(systemId), SystemIdDocument.class))
                .map(SystemIdDocument::toRegistration);
    }

    @Override
    public boolean contains(SystemId systemId) {
        return mongoTemplate.findById(SystemIdDocument.key(systemId), SystemIdDocument.class) != null;
    }

    @Override
    public void add(SystemIdRegistration registration) {
        try {
            mongoTemplate.insert(SystemIdDocument.from(registration));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("System ID already registered: " + registration.systemId(), exception);
        }
    }

    @Override
    public int count() {
        return Math.toIntExact(mongoTemplate.count(new Query(), SystemIdDocument.class));
    }
}
