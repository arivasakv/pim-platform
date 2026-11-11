package com.pim.backend.attribute.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeProperties;
import com.pim.backend.attribute.model.Cardinality;
import com.pim.backend.attribute.model.CaseFormat;
import com.pim.backend.attribute.model.CurrencyProperties;
import com.pim.backend.attribute.model.DateFormat;
import com.pim.backend.attribute.model.DateTimeProperties;
import com.pim.backend.attribute.model.DisplayName;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.NumberFormat;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.ReferenceProperties;
import com.pim.backend.attribute.model.ReferenceTargetType;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.model.TextProperties;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** Attribute definitions stored in MongoDB, collection {@code attributes} (ADR 0003; feature 004 amendment). */
@SpringBootTest
class MongoAttributeRepositoryIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-11-09T10:15:30.123Z");

    private final MongoTemplate mongoTemplate;
    private final AttributeRepository repository;

    MongoAttributeRepositoryIntegrationTest(
            @Autowired MongoTemplate mongoTemplate, @Autowired AttributeRepository repository) {
        this.mongoTemplate = mongoTemplate;
        this.repository = repository;
    }

    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), "attributes");
    }

    @Test
    void usesTheMongoImplementation() {
        assertThat(repository).isInstanceOf(MongoAttributeRepository.class);
    }

    @Test
    void roundTripsAnAttributeOfEachDataTypeWithAllProperties() {
        List<Attribute> attributes = List.of(
                attribute(
                        "ATR000001",
                        "Nombre corto",
                        AttributeDataType.TEXT,
                        new TextProperties(1, 40, CaseFormat.UPPERCASE, "[A-Z ]+", true)),
                attribute(
                        "ATR000002",
                        "Peso",
                        AttributeDataType.NUMERIC,
                        new NumericProperties(
                                NumberFormat.DECIMAL, 3, new BigDecimal("-0.500"), new BigDecimal("12345.125"))),
                attribute("ATR000003", "Apto para regalo", AttributeDataType.BOOLEAN, null),
                attribute(
                        "ATR000004",
                        "Color",
                        AttributeDataType.LIST_OF_VALUES,
                        new ListOfValuesProperties(SelectionMode.MULTIPLE, "LOV-COLOR")),
                attribute(
                        "ATR000005",
                        "Fecha de alta",
                        AttributeDataType.DATE_TIME,
                        new DateTimeProperties(DateFormat.DD_MM_YYYY_HH_MM)),
                attribute("ATR000006", "Precio base", AttributeDataType.CURRENCY, new CurrencyProperties("MXN", 4)),
                attribute(
                        "ATR000007",
                        "Producto relacionado",
                        AttributeDataType.REFERENCE,
                        new ReferenceProperties(ReferenceTargetType.PRODUCT, Cardinality.MULTIPLE)));

        attributes.forEach(repository::save);

        for (Attribute attribute : attributes) {
            Attribute found = repository
                    .findBySystemId(attribute.systemId().toLowerCase())
                    .orElseThrow();
            assertThat(found).isEqualTo(attribute);
            assertThat(found.displayName().value())
                    .isEqualTo(attribute.displayName().value());
        }
        NumericProperties weight = (NumericProperties)
                repository.findBySystemId("ATR000002").orElseThrow().properties();
        assertThat(weight.minimum()).isEqualTo(new BigDecimal("-0.500"));
        assertThat(weight.maximum()).isEqualTo(new BigDecimal("12345.125"));
        CurrencyProperties price = (CurrencyProperties)
                repository.findBySystemId("ATR000006").orElseThrow().properties();
        assertThat(price.currencySymbol()).isEqualTo("$");
    }

    @Test
    void keepsTheDescriptionAndAnAbsentOne() {
        repository.save(Attribute.create(
                "ATR000001", DisplayName.of("Peso"), "Peso neto", AttributeDataType.BOOLEAN, null, CREATED_AT));
        repository.save(Attribute.create(
                "ATR000002", DisplayName.of("Volumen"), null, AttributeDataType.BOOLEAN, null, CREATED_AT));

        assertThat(repository.findBySystemId("ATR000001").orElseThrow().description())
                .contains("Peso neto");
        assertThat(repository.findBySystemId("ATR000002").orElseThrow().description())
                .isEmpty();
    }

    @Test
    void displayNameCheckIgnoresCase() {
        repository.save(attribute("ATR000001", "Peso", AttributeDataType.BOOLEAN, null));

        assertThat(repository.existsWithDisplayName(DisplayName.of("PESO"))).isTrue();
        assertThat(repository.existsWithDisplayName(DisplayName.of("Volumen"))).isFalse();
        assertThat(repository.findBySystemId("ATR999")).isEmpty();
    }

    @Test
    void refusesADuplicateDisplayNameOrSystemId() {
        repository.save(attribute("ATR000001", "Peso", AttributeDataType.BOOLEAN, null));

        assertThatThrownBy(() -> repository.save(attribute("ATR000002", "pESO", AttributeDataType.BOOLEAN, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.save(attribute("atr000001", "Volumen", AttributeDataType.BOOLEAN, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findsAllSortedByDisplayNameIgnoringCase() {
        repository.save(attribute("ATR000001", "Volumen", AttributeDataType.BOOLEAN, null));
        repository.save(attribute("ATR000002", "peso", AttributeDataType.BOOLEAN, null));
        repository.save(attribute("ATR000003", "Color", AttributeDataType.BOOLEAN, null));

        assertThat(repository.findAll())
                .extracting(found -> found.displayName().value())
                .containsExactly("Color", "peso", "Volumen");
    }

    @Test
    void anotherInstanceReadsTheStoredData() {
        repository.save(attribute("ATR000001", "Peso", AttributeDataType.BOOLEAN, null));

        MongoAttributeRepository afterRestart = new MongoAttributeRepository(mongoTemplate);

        assertThat(afterRestart.findBySystemId("ATR000001")).isPresent();
        assertThat(afterRestart.count()).isEqualTo(1);
    }

    private static Attribute attribute(
            String systemId, String name, AttributeDataType dataType, AttributeProperties properties) {
        return Attribute.create(
                systemId, DisplayName.of(name), "Descripción de " + name, dataType, properties, CREATED_AT);
    }

    // --- Feature 007 ---

    @Test
    void updatesDeletesAndChecksNamesOfOtherAttributes() {
        Attribute peso = attribute("ATR000001", "Peso", AttributeDataType.BOOLEAN, null);
        repository.save(peso);
        repository.save(attribute("ATR000002", "Color", AttributeDataType.BOOLEAN, null));

        assertThat(repository.existsWithDisplayName(DisplayName.of("PESO"), "atr000001"))
                .isFalse();
        assertThat(repository.existsWithDisplayName(DisplayName.of("color"), "ATR000001"))
                .isTrue();

        Attribute edited = peso.updated(DisplayName.of("Peso neto"), null, null, CREATED_AT.plusSeconds(5));
        repository.update(edited);
        assertThat(repository.findBySystemId("atr000001")).contains(edited);
        assertThat(repository.existsWithDisplayName(DisplayName.of("Peso"))).isFalse();
        assertThatThrownBy(() -> repository.update(edited.updated(DisplayName.of("COLOR"), null, null, CREATED_AT)))
                .isInstanceOf(IllegalStateException.class);

        repository.delete("atr000001");
        assertThat(repository.findBySystemId("ATR000001")).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void readsADocumentWrittenBeforeFeature007AsNeverEdited() {
        mongoTemplate.insert(
                new org.bson.Document("_id", "ATR000009")
                        .append("systemId", "ATR000009")
                        .append("displayName", "Antiguo")
                        .append("displayNameKey", "antiguo")
                        .append("dataType", "BOOLEAN")
                        .append("scope", "GLOBAL")
                        .append("createdAt", java.util.Date.from(CREATED_AT)),
                "attributes");

        Attribute old = repository.findBySystemId("ATR000009").orElseThrow();
        assertThat(old.updatedAt()).isEmpty();
        assertThat(old.displayName().value()).isEqualTo("Antiguo");
    }
}
