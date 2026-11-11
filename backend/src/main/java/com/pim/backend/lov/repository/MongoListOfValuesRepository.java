package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
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
 * {@link ListOfValuesRepository} stored in MongoDB, collection {@code lists_of_values} (constitution 2.4.0; plan
 * decision D-2; ADR 0003). {@code ListOfValuesService} checks names under one lock (research R-12); the unique index on
 * the lower-cased name also refuses duplicates. If MongoDB cannot be reached, Spring's {@code
 * DataAccessResourceFailureException} propagates (503).
 */
@Repository
public class MongoListOfValuesRepository implements ListOfValuesRepository, InitializingBean {

    private final MongoTemplate mongoTemplate;

    MongoListOfValuesRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the unique name index if it does not exist yet. Safe on every start. */
    @Override
    public void afterPropertiesSet() {
        mongoTemplate
                .indexOps(ListOfValuesDocument.class)
                .createIndex(new Index()
                        .on(ListOfValuesDocument.NAME_KEY, Sort.Direction.ASC)
                        .unique()
                        .named(ListOfValuesDocument.NAME_INDEX));
    }

    @Override
    public Optional<ListOfValues> findById(String systemId) {
        if (systemId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                        mongoTemplate.findById(ListOfValuesDocument.idOf(systemId), ListOfValuesDocument.class))
                .map(ListOfValuesDocument::toList);
    }

    @Override
    public List<ListOfValues> findAll() {
        return mongoTemplate
                .find(new Query().with(Sort.by(ListOfValuesDocument.NAME_KEY)), ListOfValuesDocument.class)
                .stream()
                .map(ListOfValuesDocument::toList)
                .toList();
    }

    @Override
    public boolean existsWithName(ListName name, String excludedId) {
        Criteria criteria = Criteria.where(ListOfValuesDocument.NAME_KEY).is(name.comparisonKey());
        if (excludedId != null) {
            criteria = criteria.and("_id").ne(ListOfValuesDocument.idOf(excludedId));
        }
        return mongoTemplate.exists(Query.query(criteria), ListOfValuesDocument.class);
    }

    @Override
    public boolean hasAny() {
        return mongoTemplate.exists(new Query(), ListOfValuesDocument.class);
    }

    @Override
    public void insert(ListOfValues list) {
        try {
            mongoTemplate.insert(ListOfValuesDocument.from(list));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("List already stored: " + list.systemId(), exception);
        }
    }

    @Override
    public void update(ListOfValues list) {
        try {
            mongoTemplate.save(ListOfValuesDocument.from(list));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("List name already stored: " + list.name(), exception);
        }
    }

    @Override
    public void delete(String systemId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(ListOfValuesDocument.idOf(systemId))), ListOfValuesDocument.class);
    }
}
