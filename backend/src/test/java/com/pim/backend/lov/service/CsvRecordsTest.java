package com.pim.backend.lov.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pim.backend.lov.model.InvalidImportFileException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The CSV tokenizer shared by features 006 and 013 (feature 013 research R-02). */
class CsvRecordsTest {

    private static List<SheetRecord> read(String text) {
        return CsvRecords.read(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void numbersRecordsFromOneAndKeepsBlankOnes() {
        List<SheetRecord> records = read("Lista,ID\r\nColor,ROJ\r\n\r\nTalla,CH\n");

        assertThat(records).extracting(SheetRecord::number).containsExactly(1, 2, 3, 4);
        assertThat(records.get(1).cells()).containsExactly("Color", "ROJ");
        assertThat(records.get(2).isBlank()).isTrue();
        assertThat(records.get(3).cells()).containsExactly("Talla", "CH");
    }

    @Test
    void dropsTheBomAndPicksTheDelimiterOfTheFirstLine() {
        assertThat(read("﻿Lista;ID\nColor;\"A;B\"").get(1).cells()).containsExactly("Color", "A;B");
        assertThat(read("Lista,ID\nColor,\"Rojo \"\"vivo\"\", claro\"").get(1).cells())
                .containsExactly("Color", "Rojo \"vivo\", claro");
    }

    @Test
    void refusesAnUnclosedQuoteAndTextThatIsNotUtf8() {
        assertThatThrownBy(() -> read("Lista\n\"Color")).isInstanceOf(InvalidImportFileException.class);
        assertThatThrownBy(() -> CsvRecords.read(new byte[] {'L', (byte) 0xE9, 'n'}))
                .isInstanceOf(InvalidImportFileException.class)
                .hasMessageContaining("UTF-8");
    }

    @Test
    void readsAnEmptyFileAsNoRecords() {
        assertThat(CsvRecords.read(new byte[0])).isEmpty();
    }
}
