package com.pim.backend.identifier.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceKind;
import com.pim.backend.identifier.repository.InMemoryObjectTypeConfigurationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Default configuration of the six object types (FR-006, FR-013, plan decision D-3). Since configurations are stored
 * in MongoDB (ADR 0003), a default is only saved for a type that has no configuration yet.
 */
class DefaultObjectTypeConfigurationsTest {

    @Test
    void seedsTheSixDefaultsInObjectTypeOrder() {
        InMemoryObjectTypeConfigurationRepository repository = new InMemoryObjectTypeConfigurationRepository();

        new DefaultObjectTypeConfigurations(repository).afterPropertiesSet();

        List<ObjectTypeConfiguration> configurations = repository.findAll();
        assertThat(configurations)
                .extracting(ObjectTypeConfiguration::objectType)
                .containsExactly(ObjectType.values());
        assertThat(configurations)
                .extracting(configuration -> configuration.sequence().prefix())
                .containsExactly("PRD", "ATR", "LOV", "PLT", "CLS", "INT");
        assertThat(configurations).allSatisfy(configuration -> {
            assertThat(configuration.sequence().kind()).isEqualTo(SequenceKind.ALPHANUMERIC);
            assertThat(configuration.sequence().minimumDigits()).isEqualTo(6);
            assertThat(configuration.sequence().nextValue()).isEqualTo(1);
            assertThat(configuration.keyFields().isEmpty()).isTrue();
        });
    }

    @Test
    void doesNotOverwriteAnExistingConfiguration() {
        InMemoryObjectTypeConfigurationRepository repository = new InMemoryObjectTypeConfigurationRepository();
        ObjectTypeConfiguration advanced = new ObjectTypeConfiguration(
                ObjectType.ATTRIBUTE,
                new SequenceConfiguration(SequenceKind.ALPHANUMERIC, "ATR", 6, 42),
                KeyFieldDeclaration.empty());
        repository.save(advanced);

        new DefaultObjectTypeConfigurations(repository).seedMissing();

        assertThat(repository.find(ObjectType.ATTRIBUTE)).isEqualTo(advanced);
        assertThat(repository.findAll()).hasSize(ObjectType.values().length);
    }
}
