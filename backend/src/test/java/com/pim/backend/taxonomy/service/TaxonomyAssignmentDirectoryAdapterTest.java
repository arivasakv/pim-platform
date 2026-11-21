package com.pim.backend.taxonomy.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.TaxonomyNodeReference;
import com.pim.backend.attribute.repository.EmptyListOfValuesCatalog;
import com.pim.backend.attribute.repository.InMemoryAttributeRepository;
import com.pim.backend.attribute.repository.NoAttributeGroups;
import com.pim.backend.attribute.repository.NoTaxonomyAssignments;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import com.pim.backend.identifier.repository.InMemorySystemIdRegistry;
import com.pim.backend.identifier.service.IdentifierService;
import com.pim.backend.lov.repository.InMemoryListOfValuesRepository;
import com.pim.backend.lov.service.ListOfValuesLookup;
import com.pim.backend.taxonomy.repository.InMemoryTaxonomyNodeRepository;
import com.pim.backend.taxonomy.repository.InMemoryValueFilterRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

/** Feature 004's view of the nodes that list attributes (feature 010 plan decision D-7). */
class TaxonomyAssignmentDirectoryAdapterTest {

    private static final Instant NOW = Instant.parse("2026-11-12T18:00:00Z");
    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final AttributeService attributeService;
    private final IdentifierService identifierService;
    private final InMemoryTaxonomyNodeRepository nodes = new InMemoryTaxonomyNodeRepository();
    private final TaxonomyService service;

    {
        InMemoryObjectTypeConfigurationRepository objectTypes = new InMemoryObjectTypeConfigurationRepository();
        new DefaultObjectTypeConfigurations(objectTypes).seedMissing();
        identifierService = new IdentifierService(new InMemorySystemIdRegistry(), objectTypes, clock);
        attributeService = new AttributeService(
                new InMemoryAttributeRepository(),
                new EmptyListOfValuesCatalog(),
                new NoAttributeGroups(),
                new NoTaxonomyAssignments(),
                identifierService,
                clock);
        service = new TaxonomyService(
                nodes,
                new InMemoryValueFilterRepository(),
                attributeService,
                new ListOfValuesLookup(new InMemoryListOfValuesRepository()),
                identifierService,
                clock);
    }

    private Attribute createAttribute(String name) {
        return attributeService.create(
                new AttributeDraft(null, name, AttributeDataType.BOOLEAN, null),
                CAN_CHANGE,
                ManualIdPermissions.fromCodes(null));
    }

    @Test
    void runsTheActionOnlyWhenNoNodeListsTheAttribute() {
        Attribute color = createAttribute("Color");
        Attribute talla = createAttribute("Talla");
        String ropa = service.create("Ropa", null, null, CAN_CHANGE).node().systemId();
        service.addAttributes(ropa, List.of(color.systemId()), CAN_CHANGE);
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("taxonomyService", service);
        TaxonomyAssignmentDirectoryAdapter adapter =
                new TaxonomyAssignmentDirectoryAdapter(beans.getBeanProvider(TaxonomyService.class));
        List<String> ran = new ArrayList<>();

        assertThat(adapter.runIfOnNoNode(color.systemId().toLowerCase(), () -> ran.add("color")))
                .containsExactly(new TaxonomyNodeReference(ropa, "Ropa"));
        assertThat(adapter.runIfOnNoNode(talla.systemId(), () -> ran.add("talla")))
                .isEmpty();
        assertThat(ran).containsExactly("talla");
    }
}
