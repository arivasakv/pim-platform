package com.pim.backend.identifier.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/** Manual-ID permissions per object type (FR-003, research R-08). */
class ManualIdPermissionsTest {

    @Test
    void allowsNothingWithoutCodes() {
        for (ObjectType type : ObjectType.values()) {
            assertThat(ManualIdPermissions.fromCodes(null).allows(type)).isFalse();
            assertThat(ManualIdPermissions.fromCodes("").allows(type)).isFalse();
        }
    }

    @Test
    void allowsExactlyTheListedTypes() {
        ManualIdPermissions permissions = ManualIdPermissions.fromCodes("PRODUCT, TEMPLATE");

        assertThat(permissions.objectTypes()).isEqualTo(EnumSet.of(ObjectType.PRODUCT, ObjectType.TEMPLATE));
        assertThat(permissions.allows(ObjectType.PRODUCT)).isTrue();
        assertThat(permissions.allows(ObjectType.ATTRIBUTE)).isFalse();
    }

    @Test
    void ignoresUnknownCodesAndLetterCase() {
        ManualIdPermissions permissions = ManualIdPermissions.fromCodes("WIDGET,classification,,");

        assertThat(permissions.objectTypes()).containsExactly(ObjectType.CLASSIFICATION);
    }
}
