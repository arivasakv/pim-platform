package com.pim.backend.lov.controller;

import com.pim.backend.lov.dto.CreateListOfValuesRequest;
import com.pim.backend.lov.dto.ListOfValuesCollectionResponse;
import com.pim.backend.lov.dto.ListOfValuesResponse;
import com.pim.backend.lov.dto.UpdateListOfValuesRequest;
import com.pim.backend.lov.model.ListDetails;
import com.pim.backend.lov.service.ListOfValuesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lists of values (contract specs/006-lov-management/contracts/lists-of-values.openapi.yaml). HTTP only: the rules
 * are in {@link ListOfValuesService} (constitution III). Reading needs no permission; changes need {@code
 * ATTRIBUTE_CREATE} (FR-002).
 */
@RestController
@RequestMapping(ListOfValuesController.BASE_PATH)
public class ListOfValuesController {

    static final String BASE_PATH = "/api/v1/lists-of-values";

    private final ListOfValuesService service;
    private final LovRequestReader requestReader;

    public ListOfValuesController(ListOfValuesService service, LovRequestReader requestReader) {
        this.service = service;
        this.requestReader = requestReader;
    }

    @GetMapping
    ListOfValuesCollectionResponse list() {
        return ListOfValuesCollectionResponse.of(service.findAll());
    }

    @PostMapping
    ResponseEntity<ListOfValuesResponse> create(
            @Valid @RequestBody CreateListOfValuesRequest request, HttpServletRequest httpRequest) {
        ListDetails details = service.create(
                request.name(),
                request.systemId(),
                requestReader.permissions(httpRequest),
                requestReader.manualIdPermissions(httpRequest));
        return ResponseEntity.created(
                        URI.create(BASE_PATH + "/" + details.list().systemId()))
                .body(ListOfValuesResponse.from(details));
    }

    @GetMapping("/{listId}")
    ListOfValuesResponse find(@PathVariable String listId) {
        return ListOfValuesResponse.from(service.find(listId));
    }

    @PutMapping("/{listId}")
    ListOfValuesResponse update(
            @PathVariable String listId,
            @Valid @RequestBody UpdateListOfValuesRequest request,
            HttpServletRequest httpRequest) {
        return ListOfValuesResponse.from(service.update(
                listId, request.name(), request.equivalencesEnabled(), requestReader.permissions(httpRequest)));
    }

    @DeleteMapping("/{listId}")
    ResponseEntity<Void> delete(@PathVariable String listId, HttpServletRequest httpRequest) {
        service.delete(listId, requestReader.user(httpRequest), requestReader.permissions(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
