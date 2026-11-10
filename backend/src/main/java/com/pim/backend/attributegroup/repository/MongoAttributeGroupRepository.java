package com.pim.backend.attributegroup.repository;

import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.GroupName;
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
 * {@link AttributeGroupRepository} stored in MongoDB, collection {@code attribute_groups} (constitution IX; plan
 * decision D-1; ADR 0003). Groups survive restarts. {@code AttributeGroupService} checks names under one lock
 * (research R-12); the unique index on the lower-cased name also refuses duplicates. If MongoDB cannot be reached,
 * Spring's {@code DataAccessResourceFailureException} propagates (503).
 */
@Repository
public class MongoAttributeGroupRepository implements AttributeGroupRepository, InitializingBean {

    private final MongoTemplate mongoTemplate;

    MongoAttributeGroupRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the unique name index if it does not exist yet. Safe on every start. */
    @Override
    public void afterPropertiesSet() {
        mongoTemplate
                .indexOps(AttributeGroupDocument.class)
                .createIndex(new Index()
                        .on(AttributeGroupDocument.NAME_KEY, Sort.Direction.ASC)
                        .unique()
                        .named(AttributeGroupDocument.NAME_INDEX));
    }

    @Override
    public Optional<AttributeGroup> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, AttributeGroupDocument.class))
                .map(AttributeGroupDocument::toGroup);
    }

    @Override
    public List<AttributeGroup> findAll() {
        return mongoTemplate
                .find(new Query().with(Sort.by(AttributeGroupDocument.NAME_KEY)), AttributeGroupDocument.class)
                .stream()
                .map(AttributeGroupDocument::toGroup)
                .toList();
    }

    @Override
    public boolean existsWithName(GroupName name, String excludedId) {
        Criteria criteria = Criteria.where(AttributeGroupDocument.NAME_KEY).is(name.comparisonKey());
        if (excludedId != null) {
            criteria = criteria.and("_id").ne(excludedId);
        }
        return mongoTemplate.exists(Query.query(criteria), AttributeGroupDocument.class);
    }

    @Override
    public void insert(AttributeGroup group) {
        try {
            mongoTemplate.insert(AttributeGroupDocument.from(group));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Group already stored: " + group.name(), exception);
        }
    }

    @Override
    public void update(AttributeGroup group) {
        try {
            mongoTemplate.save(AttributeGroupDocument.from(group));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Group name already stored: " + group.name(), exception);
        }
    }

    @Override
    public void delete(String id) {
        mongoTemplate.remove(Query.query(Criteria.where("_id").is(id)), AttributeGroupDocument.class);
    }
}
