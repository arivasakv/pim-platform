package com.pim.backend.identifier.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.identifier.model.AssignmentMethod;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.SystemId;
import com.pim.backend.identifier.model.SystemIdRegistration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemorySystemIdRegistryTest {

    private final InMemorySystemIdRegistry registry = new InMemorySystemIdRegistry();

    @Test
    void findsARegistrationIgnoringLetterCase() {
        SystemIdRegistration registration = registration("CLS-ROPA");

        registry.add(registration);

        assertThat(registry.find(SystemId.of("cls-ropa"))).contains(registration);
        assertThat(registry.contains(SystemId.of("Cls-Ropa"))).isTrue();
        assertThat(registry.contains(SystemId.of("CLS-OTRA"))).isFalse();
        assertThat(registry.count()).isEqualTo(1);
    }

    @Test
    void refusesToAddAnIdAlreadyPresent() {
        registry.add(registration("CLS-ROPA"));

        assertThatThrownBy(() -> registry.add(registration("cls-ropa"))).isInstanceOf(IllegalStateException.class);
        assertThat(registry.count()).isEqualTo(1);
    }

    private static SystemIdRegistration registration(String systemId) {
        return new SystemIdRegistration(
                SystemId.of(systemId), ObjectType.CLASSIFICATION, Map.of(), AssignmentMethod.MANUAL, Instant.EPOCH);
    }
}
