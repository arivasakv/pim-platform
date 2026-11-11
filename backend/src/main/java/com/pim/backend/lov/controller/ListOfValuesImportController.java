package com.pim.backend.lov.controller;

import com.pim.backend.lov.dto.ImportResultResponse;
import com.pim.backend.lov.model.InvalidImportFileException;
import com.pim.backend.lov.service.ListOfValuesService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk value loads from CSV files and their template (FR-011, FR-016; research R-09). HTTP only: parsing and rules
 * are in the service (constitution III).
 */
@RestController
public class ListOfValuesImportController {

    /** Largest file accepted (research R-08); Spring's own multipart limit is set higher so this check answers 422. */
    static final long MAX_FILE_BYTES = 5L * 1024 * 1024;

    static final String TEMPLATE = "﻿ID,Etiqueta,Equivalencia\r\n";

    private final ListOfValuesService service;
    private final LovRequestReader requestReader;

    public ListOfValuesImportController(ListOfValuesService service, LovRequestReader requestReader) {
        this.service = service;
        this.requestReader = requestReader;
    }

    @GetMapping(ListOfValuesController.BASE_PATH + "/import-template")
    ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla-valores.csv\"")
                .body(TEMPLATE.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping(
            path = ListOfValuesController.BASE_PATH + "/{listId}/values/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportResultResponse importValues(
            @PathVariable String listId, @RequestParam("file") MultipartFile file, HttpServletRequest httpRequest) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw InvalidImportFileException.tooLarge();
        }
        return ImportResultResponse.from(service.importValues(
                listId, bytes(file), requestReader.user(httpRequest), requestReader.permissions(httpRequest)));
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
