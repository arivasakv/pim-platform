package com.pim.backend.lov.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.lov.model.ListName;
import com.pim.backend.lov.model.ListOfValues;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.model.ValueId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/** {@link MongoListOfValuesRepository} against the local test database (data-model "Collections"). */
@SpringBootTest
class MongoListOfValuesRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

    private final ListOfValuesRepository repository;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    MongoListOfValuesRepositoryIntegrationTest(
            @Autowired ListOfValuesRepository repository,
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
    void storesAndFindsAListIgnoringCaseOfItsId() {
        ListOfValues list = ListOfValues.create("LOV000001", ListName.of("Color"), NOW)
                .withEquivalencesEnabled(true, NOW)
                .withValueAdded("abc", "Azul", "BLUE", NOW)
                .list()
                .withValueAdded("0002", "Rojo", null, NOW)
                .list();

        repository.insert(list);

        assertThat(repository.findById("lov000001")).contains(list);
        ListOfValues found = repository.findById("LOV000001").orElseThrow();
        assertThat(found.values())
                .containsExactly(
                        new ListValue(ValueId.of("abc"), "Azul", Optional.of("BLUE")),
                        new ListValue(ValueId.of("0002"), "Rojo", Optional.empty()));
        assertThat(found.values().get(0).id().value()).isEqualTo("abc");
        assertThat(found.nextGeneratedNumber()).isEqualTo(1);
        assertThat(repository.findById("LOV999999")).isEmpty();
    }

    @Test
    void listsSortedByNameIgnoringCase() {
        repository.insert(ListOfValues.create("LOV000001", ListName.of("talla"), NOW));
        repository.insert(ListOfValues.create("LOV000002", ListName.of("Color"), NOW));

        assertThat(repository.findAll()).extracting(list -> list.name().value()).containsExactly("Color", "talla");
        assertThat(repository.hasAny()).isTrue();
    }

    @Test
    void checksNamesIgnoringCaseAndExcludingOneList() {
        repository.insert(ListOfValues.create("LOV000001", ListName.of("Color"), NOW));

        assertThat(repository.existsWithName(ListName.of("COLOR"), null)).isTrue();
        assertThat(repository.existsWithName(ListName.of("color"), "lov000001")).isFalse();
        assertThat(repository.existsWithName(ListName.of("Talla"), null)).isFalse();
    }

    @Test
    void updateReplacesTheWholeList() {
        ListOfValues list = ListOfValues.create("LOV000001", ListName.of("Color"), NOW);
        repository.insert(list);
        ListOfValues changed = list.withValueAdded(null, "Azul", null, NOW)
                .list()
                .renamed(ListName.of("Colores"), NOW)
                .withEquivalencesEnabled(true, NOW);

        repository.update(changed);

        assertThat(repository.findById("LOV000001")).contains(changed);
        assertThat(repository.findById("LOV000001").orElseThrow().nextGeneratedNumber())
                .isEqualTo(2);
    }

    @Test
    void deletesAList() {
        repository.insert(ListOfValues.create("LOV000001", ListName.of("Color"), NOW));

        repository.delete("lov000001");

        assertThat(repository.findById("LOV000001")).isEmpty();
        assertThat(repository.hasAny()).isFalse();
    }

    @Test
    void theUniqueIndexRefusesASecondListWithTheSameName() {
        repository.insert(ListOfValues.create("LOV000001", ListName.of("Color"), NOW));

        assertThatThrownBy(() -> repository.insert(ListOfValues.create("LOV000002", ListName.of("COLOR"), NOW)))
                .isInstanceOf(IllegalStateException.class);
    }
}
