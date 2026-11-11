package com.pim.backend.lov.repository;

import com.pim.backend.lov.model.ChangeRecord;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Repository;

/** {@link ChangeRecordRepository} stored in MongoDB, collection {@code lov_changes} (research R-11; ADR 0003). */
@Repository
public class MongoChangeRecordRepository implements ChangeRecordRepository, InitializingBean {

    private final MongoTemplate mongoTemplate;

    MongoChangeRecordRepository(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /** Creates the index {@code { listId: 1, at: -1 }} if it does not exist yet. */
    @Override
    public void afterPropertiesSet() {
        mongoTemplate
                .indexOps(ChangeRecordDocument.class)
                .createIndex(new Index()
                        .on("listId", Sort.Direction.ASC)
                        .on("at", Sort.Direction.DESC)
                        .named(ChangeRecordDocument.INDEX));
    }

    @Override
    public void insert(ChangeRecord changeRecord) {
        mongoTemplate.insert(ChangeRecordDocument.from(changeRecord));
    }
}
