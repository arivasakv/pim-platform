package com.pim.backend.attribute.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeChangeNotPermittedException;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDetails;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributeNotListOfValuesException;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.ImmutableFieldException;
import com.pim.backend.attribute.model.ListOfValuesProperties;
import com.pim.backend.attribute.model.ListUnlinkReview;
import com.pim.backend.attribute.model.NumberFormat;
import com.pim.backend.attribute.model.NumericProperties;
import com.pim.backend.attribute.model.SelectionMode;
import com.pim.backend.attribute.model.TaxonomyNodeReference;
import com.pim.backend.attribute.model.TextProperties;
import com.pim.backend.attribute.model.UnlinkOutdatedException;
import com.pim.backend.attribute.model.UnlinkToListNotAllowedException;
import com.pim.backend.attribute.repository.FakeTaxonomyAssignments;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.ListOfValuesCatalog;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unlinking a list of values from an attribute (feature 014 FR-001–FR-013; research R-02–R-05). */
class AttributeServiceUnlinkTest {

    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");
    private static final AttributePermissions NO_PERMISSION = AttributePermissions.fromCodes(null);
    private static final NumericProperties DECIMAL = new NumericProperties(NumberFormat.DECIMAL, 1, null, null);
    private static final TaxonomyNodeReference CAMISAS = new TaxonomyNodeReference("CLS000002", "Camisas");
    private static final TaxonomyNodeReference ROPA = new TaxonomyNodeReference("CLS000001", "Ropa");

    /** One list «Tallas» (LOV000001). */
    private static final ListOfValuesCatalog TALLAS = new ListOfValuesCatalog() {
        @Override
        public boolean hasAny() {
            return true;
        }

        @Override
        public boolean exists(String listOfValuesId) {
            return "LOV000001".equalsIgnoreCase(listOfValuesId);
        }

        @Override
        public Map<String, String> namesById() {
            return Map.of("LOV000001", "Tallas");
        }
    };

    private final InMemoryAttributeRepository attributes = new InMemoryAttributeRepository();
    private final FakeTaxonomyAssignments taxonomy = new FakeTaxonomyAssignments();
    private final AttributeService service;

    {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        service = new AttributeService(
                attributes,
                TALLAS,
                new NoAttributeGroups(),
                taxonomy,
                new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock),
                clock);
    }

    private Attribute talla() {
        return service.create(
                new AttributeDraft(
                        null,
                        "Talla",
                        AttributeDataType.LIST_OF_VALUES,
                        new ListOfValuesProperties(SelectionMode.SINGLE, "LOV000001")),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
    }

    @Test
    void reviewsTheListAndTheFilteredNodesWithoutChangingAnything() {
        Attribute talla = talla();
        taxonomy.putFilters(talla.systemId(), CAMISAS, ROPA);

        ListUnlinkReview review = service.reviewListUnlink(talla.systemId().toLowerCase());

        assertThat(review.attributeId()).isEqualTo(talla.systemId());
        assertThat(review.attributeName()).isEqualTo("Talla");
        assertThat(review.listOfValuesId()).isEqualTo("LOV000001");
        assertThat(review.listOfValuesName()).contains("Tallas");
        assertThat(review.filteredNodes()).containsExactly(CAMISAS, ROPA);
        assertThat(review.reviewToken()).hasSize(64);
        assertThat(service.find(talla.systemId())).isEqualTo(talla);
    }

    @Test
    void unlinksGivingTheNewTypeAndDeletingTheFilters() {
        Attribute talla = talla();
        taxonomy.putFilters(talla.systemId(), CAMISAS);
        String token = service.reviewListUnlink(talla.systemId()).reviewToken();

        AttributeDetails unlinked =
                service.unlinkList(talla.systemId(), AttributeDataType.NUMERIC, DECIMAL, token, CAN_CHANGE);

        Attribute stored = service.find(talla.systemId());
        assertThat(unlinked.attribute()).isEqualTo(stored);
        assertThat(unlinked.listOfValuesName()).isEmpty();
        assertThat(stored.dataType()).isEqualTo(AttributeDataType.NUMERIC);
        assertThat(stored.properties()).isEqualTo(DECIMAL);
        assertThat(stored.systemId()).isEqualTo(talla.systemId());
        assertThat(stored.displayName()).isEqualTo(talla.displayName());
        assertThat(stored.createdAt()).isEqualTo(talla.createdAt());
        assertThat(stored.updatedAt()).contains(NOW);
        assertThat(service.findLinkedToList("LOV000001")).isEmpty();
        assertThat(taxonomy.nodesFilteringValuesOf(talla.systemId())).isEmpty();
    }

    @Test
    void refusesAnOutdatedTokenWithoutChangingAnything() {
        Attribute talla = talla();
        String token = service.reviewListUnlink(talla.systemId()).reviewToken();
        taxonomy.putFilters(talla.systemId(), ROPA);

        assertThatThrownBy(() ->
                        service.unlinkList(talla.systemId(), AttributeDataType.NUMERIC, DECIMAL, token, CAN_CHANGE))
                .isInstanceOf(UnlinkOutdatedException.class);
        assertThatThrownBy(() ->
                        service.unlinkList(talla.systemId(), AttributeDataType.NUMERIC, DECIMAL, "otro", CAN_CHANGE))
                .isInstanceOf(UnlinkOutdatedException.class);
        assertThat(service.find(talla.systemId())).isEqualTo(talla);
        assertThat(taxonomy.nodesFilteringValuesOf(talla.systemId())).containsExactly(ROPA);
    }

    @Test
    void refusesAnAttributeWithoutAListAndAListAsTheNewType() {
        Attribute talla = talla();
        Attribute texto = service.create(
                new AttributeDraft(
                        null,
                        "Nombre corto",
                        AttributeDataType.TEXT,
                        new TextProperties(null, null, null, null, false)),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
        String token = service.reviewListUnlink(talla.systemId()).reviewToken();

        assertThatThrownBy(() -> service.reviewListUnlink(texto.systemId()))
                .isInstanceOf(AttributeNotListOfValuesException.class)
                .hasMessageContaining("Nombre corto");
        assertThatThrownBy(() ->
                        service.unlinkList(texto.systemId(), AttributeDataType.NUMERIC, DECIMAL, token, CAN_CHANGE))
                .isInstanceOf(AttributeNotListOfValuesException.class);
        assertThatThrownBy(() -> service.unlinkList(
                        talla.systemId(),
                        AttributeDataType.LIST_OF_VALUES,
                        new ListOfValuesProperties(SelectionMode.MULTIPLE, "LOV000001"),
                        token,
                        CAN_CHANGE))
                .isInstanceOf(UnlinkToListNotAllowedException.class);
        assertThat(service.find(talla.systemId())).isEqualTo(talla);
    }

    @Test
    void needsThePermissionAndAnExistingAttribute() {
        Attribute talla = talla();
        String token = service.reviewListUnlink(talla.systemId()).reviewToken();

        assertThatThrownBy(() ->
                        service.unlinkList(talla.systemId(), AttributeDataType.NUMERIC, DECIMAL, token, NO_PERMISSION))
                .isInstanceOf(AttributeChangeNotPermittedException.class);
        assertThatThrownBy(() -> service.unlinkList("ATR999999", AttributeDataType.NUMERIC, DECIMAL, token, CAN_CHANGE))
                .isInstanceOf(AttributeNotFoundException.class);
        assertThatThrownBy(() -> service.reviewListUnlink("ATR999999")).isInstanceOf(AttributeNotFoundException.class);
    }

    @Test
    void restoresTheAttributeWhenDeletingTheFiltersFails() {
        Attribute talla = talla();
        taxonomy.putFilters(talla.systemId(), ROPA);
        String token = service.reviewListUnlink(talla.systemId()).reviewToken();
        taxonomy.failRemovingFilters();

        assertThatThrownBy(() ->
                        service.unlinkList(talla.systemId(), AttributeDataType.NUMERIC, DECIMAL, token, CAN_CHANGE))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.find(talla.systemId())).isEqualTo(talla);
    }

    @Test
    void editingStillRefusesATypeChange() {
        Attribute talla = talla();

        assertThatThrownBy(() -> service.update(
                        talla.systemId(),
                        new AttributeDraft(null, "Talla", AttributeDataType.NUMERIC, DECIMAL),
                        CAN_CHANGE))
                .isInstanceOf(ImmutableFieldException.class);
        assertThat(Optional.of(service.find(talla.systemId()).dataType())).contains(AttributeDataType.LIST_OF_VALUES);
    }
}
