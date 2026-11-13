package com.pim.backend.taxonomy.repository;

import com.pim.backend.taxonomy.model.NodeName;
import com.pim.backend.taxonomy.model.TaxonomyNode;
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
 * {@link TaxonomyNodeRepository} stored in MongoDB, collection {@code taxonomy_nodes} (constitution 2.5.0 IX; plan
 * decisions D-2, D-3; ADR 0003). {@code TaxonomyService} checks names under one lock; the unique index on parent and
 * lower-cased name also refuses duplicates. If MongoDB cannot be reached, Spring's {@code
 * DataAccessResourceFailureException} propagates (503).
 */
@Repository
public class MongoTaxonomyNodeRepository implements TaxonomyNodeRepository, InitializingBean {

    private final MongoTemplate mongoTemplate;

    MongoTaxonomyNodeRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the indexes if they do not exist yet. Safe on every start. */
    @Override
    public void afterPropertiesSet() {
        var indexes = mongoTemplate.indexOps(TaxonomyNodeDocument.class);
        indexes.createIndex(new Index()
                .on(TaxonomyNodeDocument.PARENT_ID, Sort.Direction.ASC)
                .on(TaxonomyNodeDocument.NAME_KEY, Sort.Direction.ASC)
                .unique()
                .named(TaxonomyNodeDocument.NAME_INDEX));
        indexes.createIndex(new Index()
                .on(TaxonomyNodeDocument.ATTRIBUTE_IDS, Sort.Direction.ASC)
                .named(TaxonomyNodeDocument.ATTRIBUTE_INDEX));
    }

    @Override
    public Optional<TaxonomyNode> findById(String systemId) {
        return Optional.ofNullable(mongoTemplate.findById(TaxonomyNode.key(systemId), TaxonomyNodeDocument.class))
                .map(TaxonomyNodeDocument::toNode);
    }

    @Override
    public List<TaxonomyNode> findAll() {
        return find(new Query());
    }

    @Override
    public List<TaxonomyNode> findAllListing(String attributeId) {
        return find(
                Query.query(Criteria.where(TaxonomyNodeDocument.ATTRIBUTE_IDS).is(TaxonomyNode.key(attributeId))));
    }

    @Override
    public boolean existsWithName(String parentId, NodeName name, String excludedId) {
        Criteria criteria = Criteria.where(TaxonomyNodeDocument.PARENT_ID)
                .is(parentId == null ? null : TaxonomyNode.key(parentId))
                .and(TaxonomyNodeDocument.NAME_KEY)
                .is(name.comparisonKey());
        if (excludedId != null) {
            criteria = criteria.and("_id").ne(TaxonomyNode.key(excludedId));
        }
        return mongoTemplate.exists(Query.query(criteria), TaxonomyNodeDocument.class);
    }

    @Override
    public void insert(TaxonomyNode node) {
        try {
            mongoTemplate.insert(TaxonomyNodeDocument.from(node));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Node already stored: " + node.name(), exception);
        }
    }

    @Override
    public void update(TaxonomyNode node) {
        try {
            mongoTemplate.save(TaxonomyNodeDocument.from(node));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException("Node name already stored among siblings: " + node.name(), exception);
        }
    }

    @Override
    public void delete(String systemId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(TaxonomyNode.key(systemId))), TaxonomyNodeDocument.class);
    }

    private List<TaxonomyNode> find(Query query) {
        return mongoTemplate
                .find(query.with(Sort.by(TaxonomyNodeDocument.NAME_KEY)), TaxonomyNodeDocument.class)
                .stream()
                .map(TaxonomyNodeDocument::toNode)
                .toList();
    }
}
