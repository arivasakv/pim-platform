package com.pim.backend.attribute.model;

import static com.pim.backend.attribute.model.TextPropertiesTest.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;

import com.pim.backend.identifier.model.ObjectType;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Reference properties (FR-015; plan decision D-11). */
class ReferencePropertiesTest {

    @Test
    void requiresATargetObjectType() {
        assertRejected(
                () -> new ReferenceProperties(null, Cardinality.SINGLE),
                "referenceProperties.targetObjectType",
                AttributeErrorCode.REQUIRED);
    }

    @Test
    void cardinalityDefaultsToSingle() {
        ReferenceProperties properties = ReferenceProperties.of(ReferenceTargetType.PRODUCT, null);

        assertThat(properties.cardinality()).isEqualTo(Cardinality.SINGLE);
        assertThat(properties.dataType()).isEqualTo(AttributeDataType.REFERENCE);
        assertThat(ReferenceProperties.of(ReferenceTargetType.PRODUCT, Cardinality.MULTIPLE)
                        .cardinality())
                .isEqualTo(Cardinality.MULTIPLE);
    }

    @Test
    void targetTypesAreFeature003ObjectTypesInTheSameOrder() {
        assertThat(Arrays.stream(ReferenceTargetType.values()).map(Enum::name))
                .containsExactlyElementsOf(
                        Arrays.stream(ObjectType.values()).map(Enum::name).toList());
    }
}
