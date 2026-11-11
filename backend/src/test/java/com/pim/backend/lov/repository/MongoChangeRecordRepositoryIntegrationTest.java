package com.pim.backend.lov.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.lov.model.ChangeOperation;
import com.pim.backend.lov.model.ChangeRecord;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** {@link MongoChangeRecordRepository} against the local test database (data-model "ChangeRecord"). */
@SpringBootTest
class MongoChangeRecordRepositoryIntegrationTest {

    private final ChangeRecordRepository repository;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    MongoChangeRecordRepositoryIntegrationTest(
            @Autowired ChangeRecordRepository repository,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    @BeforeEach
    void startFromAFreshInstall() {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
    }

    @Test
    void storesEveryField() {
        Instant at = Instant.parse("2026-11-11T10:00:00Z");
        repository.insert(new ChangeRecord(
                "c1", at, "ana", ChangeOperation.VALUE_DELETED, "LOV000001", "Color", List.of("0002"), 0, 0));

        List<Document> stored = mongoTemplate.find(new Query(), Document.class, "lov_changes");

        assertThat(stored).hasSize(1);
        Document record = stored.get(0);
        assertThat(record.get("_id")).isEqualTo("c1");
        assertThat(record.get("at", Date.class).toInstant()).isEqualTo(at);
        assertThat(record.get("user")).isEqualTo("ana");
        assertThat(record.get("operation")).isEqualTo("VALUE_DELETED");
        assertThat(record.get("listId")).isEqualTo("LOV000001");
        assertThat(record.get("listName")).isEqualTo("Color");
        assertThat(record.getList("valueIds", String.class)).containsExactly("0002");
        assertThat(record.get("added")).isEqualTo(0);
        assertThat(record.get("updated")).isEqualTo(0);
    }

    @Test
    void hasTheIndexByListAndTime() {
        List<String> indexNames = mongoTemplate.indexOps("lov_changes").getIndexInfo().stream()
                .map(index -> index.getName())
                .toList();

        assertThat(indexNames).contains("lov_changes_list_at");
    }
}
