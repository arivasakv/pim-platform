package com.pim.backend.attributegroup.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attributegroup.model.AttributeGroup;
import com.pim.backend.attributegroup.model.GroupName;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Attribute groups stored in MongoDB, collection {@code attribute_groups} (plan decision D-1; ADR 0003). */
@SpringBootTest
class MongoAttributeGroupRepositoryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-09T10:15:30.123Z");

    private final MongoTemplate mongoTemplate;
    private final AttributeGroupRepository repository;

    MongoAttributeGroupRepositoryIntegrationTest(
            @Autowired MongoTemplate mongoTemplate, @Autowired AttributeGroupRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), "attribute_groups");
    }

    @Test
    void usesTheMongoImplementation() {
        assertThat(repository).isInstanceOf(MongoAttributeGroupRepository.class);
    }

    @Test
    void savesAndFindsAGroupWithEveryField() {
        AttributeGroup group =
                group("g-1", "Logísticos", "Dimensiones").withAttributes(List.of("ATR000002", "ATR000001"));

        repository.insert(group);

        AttributeGroup found = repository.findById("g-1").orElseThrow();
        assertThat(found).isEqualTo(group);
        assertThat(found.name().value()).isEqualTo("Logísticos");
        assertThat(found.attributeIds()).containsExactly("ATR000002", "ATR000001");
        assertThat(repository.findById("unknown")).isEmpty();
    }

    @Test
    void keepsAnAbsentDescription() {
        repository.insert(group("g-1", "Logísticos", null));

        assertThat(repository.findById("g-1").orElseThrow().description()).isEmpty();
    }

    @Test
    void findsAllSortedByNameIgnoringCase() {
        repository.insert(group("g-1", "Sistema", null));
        repository.insert(group("g-2", "compra ventas", null));
        repository.insert(group("g-3", "Logísticos", null));

        assertThat(repository.findAll())
                .extracting(found -> found.name().value())
                .containsExactly("compra ventas", "Logísticos", "Sistema");
    }

    @Test
    void nameCheckIgnoresCaseAndCanExcludeOneGroup() {
        repository.insert(group("g-1", "Logísticos", null));

        assertThat(repository.existsWithName(GroupName.of("LOGÍSTICOS"), null)).isTrue();
        assertThat(repository.existsWithName(GroupName.of("logísticos"), "g-1")).isFalse();
        assertThat(repository.existsWithName(GroupName.of("logísticos"), "g-2")).isTrue();
        assertThat(repository.existsWithName(GroupName.of("Sistema"), null)).isFalse();
    }

    @Test
    void theUniqueIndexRefusesADuplicateName() {
        repository.insert(group("g-1", "Logísticos", null));

        assertThatThrownBy(() -> repository.insert(group("g-2", "LOGÍSTICOS", null)))
                .isInstanceOf(IllegalStateException.class);
        repository.insert(group("g-3", "Sistema", null));
        assertThatThrownBy(() -> repository.update(group("g-3", "logísticos", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    void updateReplacesTheGroupWithTheSameIdentifier() {
        repository.insert(group("g-1", "Logisticos", null));
        AttributeGroup renamed = repository
                .findById("g-1")
                .orElseThrow()
                .withAttributes(List.of("ATR000001"))
                .renamed(GroupName.of("Logísticos"), "Peso y volumen");

        repository.update(renamed);

        assertThat(repository.findById("g-1")).contains(renamed);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void deletesAGroup() {
        repository.insert(group("g-1", "Logísticos", null));

        repository.delete("g-1");

        assertThat(repository.findById("g-1")).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void anotherInstanceReadsTheStoredData() {
        repository.insert(group("g-1", "Logísticos", null));

        MongoAttributeGroupRepository afterRestart = new MongoAttributeGroupRepository(mongoTemplate);

        assertThat(afterRestart.findById("g-1")).isPresent();
    }

    private static AttributeGroup group(String id, String name, String description) {
        return AttributeGroup.create(id, GroupName.of(name), description, CREATED_AT);
    }
}
