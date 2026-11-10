package com.pim.backend.attribute.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Permission to create attributes (FR-002, FR-019; plan decision D-2). */
class AttributePermissionsTest {

    @Test
    void allowsNothingWithoutCodes() {
        assertThat(AttributePermissions.fromCodes(null).allowsCreation()).isFalse();
        assertThat(AttributePermissions.fromCodes("").allowsCreation()).isFalse();
        assertThat(AttributePermissions.fromCodes("OTHER").allowsCreation()).isFalse();
    }

    @Test
    void allowsCreationWithTheCodeTrimmedAndIgnoringCase() {
        assertThat(AttributePermissions.fromCodes(" attribute_create , X ").allowsCreation())
                .isTrue();
        assertThat(AttributePermissions.fromCodes("ATTRIBUTE_CREATE").allowsCreation())
                .isTrue();
    }
}
