package com.pim.backend.lov.controller;

import com.pim.backend.lov.dto.BulkLoadResultResponse;
import com.pim.backend.lov.dto.BulkLoadSummaryResponse;
import com.pim.backend.lov.model.InvalidBulkLoadRequestException;
import com.pim.backend.lov.model.InvalidImportFileException;
import com.pim.backend.lov.model.LovErrorCode;
import com.pim.backend.lov.service.BulkLoadTemplates;
import com.pim.backend.lov.service.ListOfValuesService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bulk loads of several lists of values from a CSV or Excel file, in two steps: check, then apply (feature 013
 * contract {@code lov-bulk-load.openapi.yaml}; research R-05). HTTP only: reading and rules are in the service
 * (constitution III).
 */
@RestController
public class ListOfValuesBulkLoadController {

    static final String BULK_LOAD_PATH = ListOfValuesController.BASE_PATH + "/bulk-load";

    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private static final MediaType EXCEL =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final String TEMPLATE_NAME = "plantilla-listas-de-valores";

    private final ListOfValuesService service;
    private final LovRequestReader requestReader;

    public ListOfValuesBulkLoadController(ListOfValuesService service, LovRequestReader requestReader) {
        this.service = service;
        this.requestReader = requestReader;
    }

    /** The template in Excel ({@code format=xlsx}) or CSV (default) (feature 013 FR-003, research R-12). */
    @GetMapping(BULK_LOAD_PATH + "/template")
    ResponseEntity<byte[]> template(@RequestParam(name = "format", defaultValue = "csv") String format) {
        boolean excel = switch (format.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "xlsx" -> true;
            case "csv" -> false;
            default -> throw new InvalidBulkLoadRequestException("format", LovErrorCode.INVALID_FORMAT);
        };
        return ResponseEntity.ok()
                .contentType(excel ? EXCEL : CSV)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"%s.%s\"".formatted(TEMPLATE_NAME, excel ? "xlsx" : "csv"))
                .body(excel ? BulkLoadTemplates.excel() : BulkLoadTemplates.csv());
    }

    @PostMapping(path = BULK_LOAD_PATH + "/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BulkLoadSummaryResponse check(@RequestParam("file") MultipartFile file, HttpServletRequest httpRequest) {
        return BulkLoadSummaryResponse.from(service.checkBulkLoad(bytes(file), requestReader.permissions(httpRequest)));
    }

    @PostMapping(path = BULK_LOAD_PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    BulkLoadResultResponse apply(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "checkToken", required = false) String checkToken,
            HttpServletRequest httpRequest) {
        if (checkToken == null || checkToken.isBlank()) {
            throw new InvalidBulkLoadRequestException("checkToken", LovErrorCode.CHECK_TOKEN_REQUIRED);
        }
        return BulkLoadResultResponse.from(service.applyBulkLoad(
                bytes(file),
                checkToken.strip(),
                requestReader.user(httpRequest),
                requestReader.permissions(httpRequest)));
    }

    /** The file's bytes, refusing more than 5 MB (feature 006 research R-08, feature 013 R-09). */
    private static byte[] bytes(MultipartFile file) {
        if (file.getSize() > ListOfValuesImportController.MAX_FILE_BYTES) {
            throw InvalidImportFileException.tooLarge();
        }
        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
