package com.pim.backend.attribute.repository;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.DisplayName;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * {@link AttributeRepository} stored in MongoDB, collection {@code attributes} (constitution IX; ADR 0003). Attributes
 * survive restarts. {@code AttributeService} checks display names under one lock (research R-18); the {@code _id}
 * and the unique index on the lower-cased display name also refuse duplicates. If MongoDB cannot be reached,
 * Spring's {@code DataAccessResourceFailureException} propagates (503).
 */
@Repository
public class MongoAttributeRepository implements AttributeRepository, InitializingBean {

    private final MongoTemplate mongoTemplate;

    MongoAttributeRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the unique display-name index if it does not exist yet. Safe on every start. */
    @Override
    public void afterPropertiesSet() {
        mongoTemplate
                .indexOps(AttributeDocument.class)
                .createIndex(new Index()
                        .on(AttributeDocument.DISPLAY_NAME_KEY, Sort.Direction.ASC)
                        .unique()
                        .named(AttributeDocument.DISPLAY_NAME_INDEX));
    }

    @Override
    public Optional<Attribute> findBySystemId(String systemId) {
        return Optional.ofNullable(mongoTemplate.findById(AttributeDocument.key(systemId), AttributeDocument.class))
                .map(AttributeDocument::toAttribute);
    }

    @Override
    public boolean existsWithDisplayName(DisplayName displayName) {
        return mongoTemplate.exists(
                Query.query(Criteria.where(AttributeDocument.DISPLAY_NAME_KEY).is(displayName.comparisonKey())),
                AttributeDocument.class);
    }

    @Override
    public void save(Attribute attribute) {
        try {
            mongoTemplate.insert(AttributeDocument.from(attribute));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Attribute already stored: " + attribute.systemId(), exception);
        }
    }

    @Override
    public List<Attribute> findAll() {
        return mongoTemplate
                .find(new Query().with(Sort.by(AttributeDocument.DISPLAY_NAME_KEY)), AttributeDocument.class)
                .stream()
                .map(AttributeDocument::toAttribute)
                .toList();
    }

    @Override
    public int count() {
        return Math.toIntExact(mongoTemplate.count(new Query(), AttributeDocument.class));
    }
}
