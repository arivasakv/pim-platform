package com.pim.backend.identifier.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.identifier.model.AssignmentMethod;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdRegistration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/** The system ID registry stored in MongoDB, collection {@code system_ids} (ADR 0003; feature 003 amendment). */
@SpringBootTest
class MongoSystemIdRegistryIntegrationTest {

    private static final Instant REGISTERED_AT = Instant.parse("2026-11-09T10:15:30Z");

    private final MongoTemplate mongoTemplate;
    private final SystemIdRegistry registry;

    MongoSystemIdRegistryIntegrationTest(@Autowired MongoTemplate mongoTemplate, @Autowired SystemIdRegistry registry) {
        this.mongoTemplate = mongoTemplate;
        this.registry = registry;
    }

    @BeforeEach
    void emptyTheCollection() {
        mongoTemplate.remove(new Query(), "system_ids");
    }

    @Test
    void usesTheMongoImplementation() {
        assertThat(registry).isInstanceOf(MongoSystemIdRegistry.class);
    }

    @Test
    void findsARegistrationIgnoringCaseWithEveryField() {
        Map<String, String> keyFields = new LinkedHashMap<>();
        keyFields.put("zeta", "último");
        keyFields.put("alpha", "primero");
        registry.add(new SystemIdRegistration(
                SystemId.of("Atr-001"), ObjectType.ATTRIBUTE, keyFields, AssignmentMethod.MANUAL, REGISTERED_AT));

        SystemIdRegistration found = registry.find(SystemId.of("ATR-001")).orElseThrow();

        assertThat(found.systemId().value()).isEqualTo("Atr-001");
        assertThat(found.objectType()).isEqualTo(ObjectType.ATTRIBUTE);
        assertThat(found.keyFieldValues()).containsExactly(Map.entry("zeta", "último"), Map.entry("alpha", "primero"));
        assertThat(found.assignment()).isEqualTo(AssignmentMethod.MANUAL);
        assertThat(found.registeredAt()).isEqualTo(REGISTERED_AT);
    }

    @Test
    void containsAndCountIgnoreCase() {
        registry.add(registration("PRD000001"));
        registry.add(registration("PRD000002"));

        assertThat(registry.contains(SystemId.of("prd000001"))).isTrue();
        assertThat(registry.contains(SystemId.of("PRD000003"))).isFalse();
        assertThat(registry.find(SystemId.of("PRD000003"))).isEmpty();
        assertThat(registry.count()).isEqualTo(2);
    }

    @Test
    void refusesAnIdAlreadyStoredInAnotherCase() {
        registry.add(registration("PRD000001"));

        assertThatThrownBy(() -> registry.add(registration("prd000001"))).isInstanceOf(IllegalStateException.class);
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void anotherInstanceReadsTheStoredData() {
        registry.add(registration("PRD000001"));

        MongoSystemIdRegistry afterRestart = new MongoSystemIdRegistry(mongoTemplate);

        assertThat(afterRestart.contains(SystemId.of("PRD000001"))).isTrue();
        assertThat(afterRestart.count()).isEqualTo(1);
    }

    private static SystemIdRegistration registration(String id) {
        return new SystemIdRegistration(
                SystemId.of(id), ObjectType.PRODUCT, Map.of(), AssignmentMethod.AUTOMATIC, REGISTERED_AT);
    }
}
