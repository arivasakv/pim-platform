package com.pim.backend.identifier.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Object-type configurations stored in MongoDB, collection {@code object_type_configurations} (ADR 0003; feature 003
 * amendment).
 */
@SpringBootTest
class MongoObjectTypeConfigurationRepositoryIntegrationTest {

    private final MongoTemplate mongoTemplate;
    private final ObjectTypeConfigurationRepository repository;

    MongoObjectTypeConfigurationRepositoryIntegrationTest(
            @Autowired MongoTemplate mongoTemplate, @Autowired ObjectTypeConfigurationRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), "object_type_configurations");
    }

    @Test
    void usesTheMongoImplementation() {
        assertThat(repository).isInstanceOf(MongoObjectTypeConfigurationRepository.class);
    }

    @Test
    void roundTripsAlphanumericAndNumericSequencesWithKeyFields() {
        ObjectTypeConfiguration attribute = new ObjectTypeConfiguration(
                ObjectType.ATTRIBUTE,
                new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "ATR", 6, 42),
                new KeyFieldDeclaration(List.of("displayName", "code")));
        ObjectTypeConfiguration product = new ObjectTypeConfiguration(
                ObjectType.PRODUCT,
                new SequenceConfiguration(SequenceKind.NUMERIC, null, 18, 999_999_999_999_999_999L),
                KeyFieldDeclaration.empty());

        repository.save(attribute);
        repository.save(product);

        assertThat(repository.find(ObjectType.ATTRIBUTE)).isEqualTo(attribute);
        assertThat(repository.find(ObjectType.PRODUCT)).isEqualTo(product);
    }

    @Test
    void findsAllInObjectTypeOrder() {
        repository.save(defaults(ObjectType.INTERFACE, "INT"));
        repository.save(defaults(ObjectType.PRODUCT, "PRD"));
        repository.save(defaults(ObjectType.TEMPLATE, "PLT"));

        assertThat(repository.findAll())
                .extracting(ObjectTypeConfiguration::objectType)
                .containsExactly(ObjectType.PRODUCT, ObjectType.TEMPLATE, ObjectType.INTERFACE);
    }

    @Test
    void containsOnlyConfiguredTypes() {
        repository.save(defaults(ObjectType.PRODUCT, "PRD"));

        assertThat(repository.contains(ObjectType.PRODUCT)).isTrue();
        assertThat(repository.contains(ObjectType.ATTRIBUTE)).isFalse();
    }

    @Test
    void findingAMissingTypeFails() {
        assertThatThrownBy(() -> repository.find(ObjectType.CLASSIFICATION)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void saveReplacesThePreviousConfiguration() {
        repository.save(defaults(ObjectType.PRODUCT, "PRD"));
        ObjectTypeConfiguration replaced = defaults(ObjectType.PRODUCT, "SKU")
                .withSequence(new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "SKU", 8, 7));

        repository.save(replaced);

        assertThat(repository.find(ObjectType.PRODUCT)).isEqualTo(replaced);
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void anotherInstanceReadsTheStoredData() {
        repository.save(defaults(ObjectType.PRODUCT, "PRD"));

        MongoObjectTypeConfigurationRepository afterRestart = new MongoObjectTypeConfigurationRepository(mongoTemplate);

        assertThat(afterRestart.find(ObjectType.PRODUCT).sequence().prefix()).isEqualTo("PRD");
    }

    private static ObjectTypeConfiguration defaults(ObjectType type, String prefix) {
        return new ObjectTypeConfiguration(
                type, new SequenceConfiguration(SequenceKind.ALPHANUMERIC, prefix, 6, 1), KeyFieldDeclaration.empty());
    }
}
