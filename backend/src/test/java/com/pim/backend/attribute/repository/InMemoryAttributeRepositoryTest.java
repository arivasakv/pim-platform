package com.pim.backend.attribute.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.attribute.model.Attribute;
import com.pim.backend.attribute.model.AttributeDataType;
import com.pim.backend.attribute.model.DisplayName;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InMemoryAttributeRepositoryTest {

    private final InMemoryAttributeRepository repository = new InMemoryAttributeRepository();

    @Test
    void findsBySystemIdIgnoringCaseAndKnowsTheDisplayNames() {
        Attribute color = attribute("ATR-COLOR", "Color");

        repository.save(color);

        assertThat(repository.findBySystemId("atr-color")).contains(color);
        assertThat(repository.findBySystemId("ATR-OTRO")).isEmpty();
        assertThat(repository.existsWithDisplayName(DisplayName.of(" COLOR "))).isTrue();
        assertThat(repository.existsWithDisplayName(DisplayName.of("Talla"))).isFalse();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void refusesADuplicateIdOrDisplayName() {
        repository.save(attribute("ATR-COLOR", "Color"));

        assertThatThrownBy(() -> repository.save(attribute("atr-color", "Talla")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> repository.save(attribute("ATR-2", "color")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    private static Attribute attribute(String systemId, String name) {
        return Attribute.create(systemId, DisplayName.of(name), null, AttributeDataType.BOOLEAN, null, Instant.EPOCH);
    }
}
