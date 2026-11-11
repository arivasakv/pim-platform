package com.pim.backend.attributegroup.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.AttributeDraft;
import com.pim.backend.attribute.model.AttributePermissions;
import com.pim.backend.attribute.model.GroupReference;
import com.pim.backend.attribute.repository.AttributeGroupDirectory;
import com.pim.backend.attribute.service.AttributeService;
import com.pim.backend.common.AttributeAdministrationTestData;
import com.pim.backend.identifier.config.DefaultObjectTypeConfigurations;
import com.pim.backend.identifier.model.ManualIdPermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * Feature 004's view of the groups, implemented by this feature (feature 007 plan decision D-2): the adapter is the
 * application's {@link AttributeGroupDirectory}, and the bean graph has no cycle.
 */
@SpringBootTest
class AttributeGroupDirectoryAdapterTest {

    private static final AttributePermissions CAN_CHANGE = AttributePermissions.fromCodes("ATTRIBUTE_CREATE");

    private final AttributeGroupDirectory directory;
    private final AttributeGroupService groupService;
    private final AttributeService attributeService;
    private final MongoTemplate mongoTemplate;
    private final DefaultObjectTypeConfigurations defaults;

    AttributeGroupDirectoryAdapterTest(
            @Autowired AttributeGroupDirectory directory,
            @Autowired AttributeGroupService groupService,
            @Autowired AttributeService attributeService,
            @Autowired MongoTemplate mongoTemplate,
            @Autowired DefaultObjectTypeConfigurations defaults) {
        this.directory = directory;
        this.groupService = groupService;
        this.attributeService = attributeService;
        this.mongoTemplate = mongoTemplate;
        this.defaults = defaults;
    }

    @BeforeEach
    void startFromAFreshInstall() {
        AttributeAdministrationTestData.reset(mongoTemplate, defaults);
    }

    @Test
    void isTheApplicationsDirectory() {
        assertThat(directory).isInstanceOf(AttributeGroupDirectoryAdapter.class);
    }

    @Test
    void mapsEachAttributeToItsGroupsSortedByName() {
        String peso = createAttribute("Peso");
        String color = createAttribute("Color");
        String ventas = groupService.create("Ventas", null, CAN_CHANGE).group().id();
        String logisticos =
                groupService.create("logísticos", null, CAN_CHANGE).group().id();
        groupService.addAttributes(ventas, List.of(peso), CAN_CHANGE);
        groupService.addAttributes(logisticos, List.of(peso.toLowerCase()), CAN_CHANGE);

        assertThat(directory.groupsByAttribute())
                .containsOnlyKeys(peso)
                .containsEntry(
                        peso,
                        List.of(new GroupReference(logisticos, "logísticos"), new GroupReference(ventas, "Ventas")));
        assertThat(directory.groupsByAttribute()).doesNotContainKey(color);
    }

    @Test
    void runsTheActionOnlyForAnAttributeInNoGroup() {
        String peso = createAttribute("Peso");
        String color = createAttribute("Color");
        String ventas = groupService.create("Ventas", null, CAN_CHANGE).group().id();
        groupService.addAttributes(ventas, List.of(peso), CAN_CHANGE);
        List<String> ran = new ArrayList<>();

        assertThat(directory.runIfInNoGroup(peso.toLowerCase(), () -> ran.add("peso")))
                .containsExactly(new GroupReference(ventas, "Ventas"));
        assertThat(directory.runIfInNoGroup(color, () -> ran.add("color"))).isEmpty();
        assertThat(ran).containsExactly("color");
    }

    private String createAttribute(String name) {
        return attributeService
                .create(
                        new AttributeDraft(null, name, null, AttributeDataType.BOOLEAN, null),
                        CAN_CHANGE,
                        ManualIdPermissions.fromCodes(null))
                .systemId();
    }
}
