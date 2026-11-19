package com.pim.backend.taxonomy.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** List-value filters (feature 012 FR-002; data-model "List-value filter"). */
class ValueFilterTest {

    private static final Instant CREATED = Instant.parse("2026-11-19T10:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-19T11:00:00Z");
    private static final String TOO_FEW_OR_MANY =
            "Incluye de 1 a 50000 valores en el filtro. Para permitir todos los valores, quita el filtro.";

    @Test
    void upperCasesTheKeysAndKeepsEachValueOnce() {
        ValueFilter filter =
                ValueFilter.create("cls000002", "atr000001", List.of("0002", " 0004 ", "a1", "A1"), CREATED);

        assertThat(filter.nodeId()).isEqualTo("CLS000002");
        assertThat(filter.attributeId()).isEqualTo("ATR000001");
        assertThat(filter.valueIds()).containsExactly("0002", "0004", "a1");
        assertThat(filter.valueKeys()).containsExactlyInAnyOrder("0002", "0004", "A1");
        assertThat(filter.id()).isEqualTo("CLS000002:ATR000001");
        assertThat(filter.updatedAt()).isEmpty();
    }

    @Test
    void replacingTheValuesKeepsTheCreationDate() {
        ValueFilter filter = ValueFilter.create("CLS000002", "ATR000001", List.of("0002"), CREATED)
                .withValues(List.of("0005", "0001"), LATER);

        assertThat(filter.valueIds()).containsExactly("0005", "0001");
        assertThat(filter.createdAt()).isEqualTo(CREATED);
        assertThat(filter.updatedAt()).contains(LATER);
    }

    @Test
    void needsOneToFiftyThousandValues() {
        List<String> tooMany =
                IntStream.rangeClosed(1, 50_001).mapToObj(Integer::toString).toList();

        assertThatThrownBy(() -> ValueFilter.create("CLS000002", "ATR000001", List.of(), CREATED))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessage(TOO_FEW_OR_MANY);
        assertThatThrownBy(() -> ValueFilter.checkCount(List.of(" ", ""))).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> ValueFilter.checkCount(null)).isInstanceOf(InvalidFilterException.class);
        assertThatThrownBy(() -> ValueFilter.checkCount(tooMany)).isInstanceOf(InvalidFilterException.class);
        ValueFilter.checkCount(tooMany.subList(0, 50_000));
    }
}
