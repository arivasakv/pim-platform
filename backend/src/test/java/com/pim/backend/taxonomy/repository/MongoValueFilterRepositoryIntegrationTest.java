package com.pim.backend.taxonomy.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.taxonomy.model.ValueFilter;
import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** List-value filters stored in MongoDB, collection {@code taxonomy_value_filters} (feature 012 plan D-2). */
@SpringBootTest
class MongoValueFilterRepositoryIntegrationTest {

    private static final Instant CREATED = Instant.parse("2026-11-19T10:15:30.123Z");
    private static final Instant UPDATED = Instant.parse("2026-11-19T11:00:00.456Z");

    private final MongoTemplate mongoTemplate;
    private final ValueFilterRepository repository;

    MongoValueFilterRepositoryIntegrationTest(
            @Autowired MongoTemplate mongoTemplate, @Autowired ValueFilterRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), "taxonomy_value_filters");
    }

    @Test
    void usesTheMongoImplementation() {
        assertThat(repository).isInstanceOf(MongoValueFilterRepository.class);
    }

    @Test
    void savesReplacesAndFindsFiltersIgnoringCase() {
        ValueFilter filter = ValueFilter.create("CLS000002", "ATR000001", List.of("0002", "0004"), CREATED);
        repository.save(filter);
        repository.save(filter.withValues(List.of("0005"), UPDATED));

        ValueFilter found = repository.find("cls000002", "atr000001").orElseThrow();

        assertThat(found.valueIds()).containsExactly("0005");
        assertThat(found.createdAt()).isEqualTo(CREATED);
        assertThat(found.updatedAt()).contains(UPDATED);
        assertThat(mongoTemplate.count(new Query(), "taxonomy_value_filters")).isEqualTo(1);
    }

    @Test
    void findsByNodesAndDeletes() {
        repository.save(ValueFilter.create("CLS000001", "ATR000001", List.of("0001"), CREATED));
        repository.save(ValueFilter.create("CLS000002", "ATR000001", List.of("0002"), CREATED));
        repository.save(ValueFilter.create("CLS000002", "ATR000009", List.of("X"), CREATED));
        repository.save(ValueFilter.create("CLS000003", "ATR000001", List.of("0003"), CREATED));

        assertThat(repository.findByNodeIds(List.of("cls000001", "CLS000002")))
                .extracting(ValueFilter::id)
                .containsExactlyInAnyOrder("CLS000001:ATR000001", "CLS000002:ATR000001", "CLS000002:ATR000009");

        repository.delete("CLS000001", "atr000001");
        repository.deleteByNode("cls000002");

        assertThat(repository.findByNodeIds(List.of("CLS000001", "CLS000002", "CLS000003")))
                .extracting(ValueFilter::id)
                .containsExactly("CLS000003:ATR000001");
    }

    @Test
    void findsAndDeletesTheFiltersOfOneAttributeOnly() {
        repository.save(ValueFilter.create("CLS000001", "ATR000001", List.of("0001"), CREATED));
        repository.save(ValueFilter.create("CLS000002", "ATR000001", List.of("0002"), CREATED));
        repository.save(ValueFilter.create("CLS000002", "ATR000009", List.of("X"), CREATED));

        assertThat(repository.findByAttribute("atr000001"))
                .extracting(ValueFilter::id)
                .containsExactlyInAnyOrder("CLS000001:ATR000001", "CLS000002:ATR000001");

        repository.deleteByAttribute("Atr000001");

        assertThat(repository.findByAttribute("ATR000001")).isEmpty();
        assertThat(repository.findByAttribute("ATR000009"))
                .extracting(ValueFilter::id)
                .containsExactly("CLS000002:ATR000009");
    }

    @Test
    void createsTheUniqueIndex() {
        List<Document> indexes = mongoTemplate
                .getCollection("taxonomy_value_filters")
                .listIndexes()
                .into(new java.util.ArrayList<>());

        assertThat(indexes).anySatisfy(index -> {
            assertThat(index.getString("name")).isEqualTo("taxonomy_value_filter_unique");
            assertThat(index.getBoolean("unique")).isTrue();
        });
    }
}
