package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.TaxonomyNode;
import com.pim.backend.taxonomy.model.ValueFilter;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * {@link ValueFilterRepository} stored in MongoDB, collection {@code taxonomy_value_filters} (feature 012 plan D-2;
 * constitution 2.6.0 IX; ADR 0003). One document per node and attribute, so each save or removal is atomic. If
 * MongoDB cannot be reached, Spring's {@code DataAccessResourceFailureException} propagates (503).
 */
@Repository
public class MongoValueFilterRepository implements ValueFilterRepository, InitializingBean {

    private final MongoTemplate mongoTemplate;

    MongoValueFilterRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the unique index if it does not exist yet. Safe on every start. */
    @Override
    public void afterPropertiesSet() {
        mongoTemplate
                .indexOps(ValueFilterDocument.class)
                .createIndex(new Index()
                        .on(ValueFilterDocument.NODE_ID, Sort.Direction.ASC)
                        .on(ValueFilterDocument.ATTRIBUTE_ID, Sort.Direction.ASC)
                        .unique()
                        .named(ValueFilterDocument.UNIQUE_INDEX));
    }

    @Override
    public Optional<ValueFilter> find(String nodeId, String attributeId) {
        return Optional.ofNullable(mongoTemplate.findById(key(nodeId, attributeId), ValueFilterDocument.class))
                .map(ValueFilterDocument::toFilter);
    }

    @Override
    public List<ValueFilter> findByNodeIds(Collection<String> nodeIds) {
        List<String> keys = nodeIds.stream().map(TaxonomyNode::key).toList();
        return mongoTemplate
                .find(Query.query(Criteria.where(ValueFilterDocument.NODE_ID).in(keys)), ValueFilterDocument.class)
                .stream()
                .map(ValueFilterDocument::toFilter)
                .toList();
    }

    @Override
    public void save(ValueFilter filter) {
        mongoTemplate.save(ValueFilterDocument.from(filter));
    }

    @Override
    public void delete(String nodeId, String attributeId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(key(nodeId, attributeId))), ValueFilterDocument.class);
    }

    @Override
    public void deleteByNode(String nodeId) {
        mongoTemplate.remove(
                Query.query(Criteria.where(ValueFilterDocument.NODE_ID).is(TaxonomyNode.key(nodeId))),
                ValueFilterDocument.class);
    }

    private static String key(String nodeId, String attributeId) {
        return TaxonomyNode.key(nodeId) + ":" + TaxonomyNode.key(attributeId);
    }
}
