package com.pim.backend.identifier.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Key-field declaration of an object type (FR-013, data-model "KeyFieldDeclaration"). */
class KeyFieldDeclarationTest {

    @Test
    void mayBeEmpty() {
        assertThat(KeyFieldDeclaration.empty().fieldNames()).isEmpty();
        assertThat(new KeyFieldDeclaration(List.of()).isEmpty()).isTrue();
    }

    @Test
    void keepsTheOrderGivenAndCannotBeChanged() {
        KeyFieldDeclaration declaration = new KeyFieldDeclaration(new ArrayList<>(List.of("sku", "business_unit")));

        assertThat(declaration.fieldNames()).containsExactly("sku", "business_unit");
        assertThatThrownBy(() -> declaration.fieldNames().add("other"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void acceptsTwentyNamesOfUpToSixtyCharacters() {
        List<String> names =
                IntStream.rangeClosed(1, 20).mapToObj(i -> "field" + i).toList();

        assertThat(new KeyFieldDeclaration(names).fieldNames()).hasSize(20);
        assertThat(new KeyFieldDeclaration(List.of("a" + "b".repeat(59))).fieldNames())
                .hasSize(1);
    }

    @Test
    void rejectsInvalidNamesRepeatsAndMoreThanTwenty() {
        assertRejected(List.of("1code"));
        assertRejected(List.of(""));
        assertRejected(List.of("co de"));
        assertRejected(List.of("a" + "b".repeat(60)));
        assertRejected(List.of("code", "code"));
        assertRejected(IntStream.rangeClosed(1, 21).mapToObj(i -> "field" + i).toList());
        assertRejected(Arrays.asList("code", null));
        assertRejected(null);
    }

    private static void assertRejected(List<String> names) {
        assertThatThrownBy(() -> new KeyFieldDeclaration(names))
                .isInstanceOfSatisfying(InvalidIdentifierRequestException.class, exception -> {
                    assertThat(exception.field()).isEqualTo("keyFields");
                    assertThat(exception.code()).isEqualTo(IdentifierErrorCode.INVALID_KEY_FIELD_NAME);
                });
    }
}
