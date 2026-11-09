package com.pim.backend.identifier.config;

import com.pim.backend.identifier.model.KeyFieldDeclaration;
import com.pim.backend.identifier.model.ObjectType;
import com.pim.backend.identifier.model.ObjectTypeConfiguration;
import com.pim.backend.identifier.model.SequenceConfiguration;
import com.pim.backend.identifier.model.SequenceKind;
import com.pim.backend.identifier.repository.ObjectTypeConfigurationRepository;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Seeds the default configuration of the six object types when the application starts, before the web server
 * accepts requests (spec FR-006, FR-013; research R-05, plan decision D-3): alphanumeric, 6 digits, starting at 1,
 * one prefix per type, and no key fields.
 */
@Component
public class DefaultObjectTypeConfigurations implements InitializingBean {

    private static final int DEFAULT_MINIMUM_DIGITS = 6;
    private static final long DEFAULT_FIRST_VALUE = 1;

    private static final Map<ObjectType, String> DEFAULT_PREFIXES = new EnumMap<>(Map.of(
            ObjectType.PRODUCT, "PRD",
            ObjectType.ATTRIBUTE, "ATR",
            ObjectType.LIST_OF_VALUES, "LOV",
            ObjectType.TEMPLATE, "PLT",
            ObjectType.CLASSIFICATION, "CLS",
            ObjectType.INTERFACE, "INT"));

    private final ObjectTypeConfigurationRepository repository;

    public DefaultObjectTypeConfigurations(ObjectTypeConfigurationRepository repository) {
        this.repository = repository;
    }

    @Override
    public void afterPropertiesSet() {
        DEFAULT_PREFIXES.forEach((type, prefix) -> repository.save(new ObjectTypeConfiguration(
                type,
                new SequenceConfiguration(
                        SequenceKind.ALPHANUMERIC, prefix, DEFAULT_MINIMUM_DIGITS, DEFAULT_FIRST_VALUE),
                KeyFieldDeclaration.empty())));
    }
}
