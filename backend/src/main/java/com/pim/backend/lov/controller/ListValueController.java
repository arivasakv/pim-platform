package com.pim.backend.lov.controller;

import com.pim.backend.lov.dto.AddValueRequest;
import com.pim.backend.lov.dto.ListValueResponse;
import com.pim.backend.lov.dto.UpdateValueRequest;
import com.pim.backend.lov.model.ListValue;
import com.pim.backend.lov.service.ListOfValuesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The values of one list, one at a time (FR-009, FR-010, FR-013). HTTP only (constitution III). */
@RestController
@RequestMapping(ListOfValuesController.BASE_PATH + "/{listId}/values")
public class ListValueController {

    private final ListOfValuesService service;
    private final LovRequestReader requestReader;

    public ListValueController(ListOfValuesService service, LovRequestReader requestReader) {
        this.service = service;
        this.requestReader = requestReader;
    }

    @PostMapping
    ResponseEntity<ListValueResponse> add(
            @PathVariable String listId, @Valid @RequestBody AddValueRequest request, HttpServletRequest httpRequest) {
        ListValue value = service.addValue(
                listId, request.id(), request.label(), request.equivalence(), requestReader.permissions(httpRequest));
        return ResponseEntity.created(
                        URI.create(ListOfValuesController.BASE_PATH + "/" + listId + "/values/" + value.id()))
                .body(ListValueResponse.from(value));
    }

    @PutMapping("/{valueId}")
    ListValueResponse update(
            @PathVariable String listId,
            @PathVariable String valueId,
            @Valid @RequestBody UpdateValueRequest request,
            HttpServletRequest httpRequest) {
        return ListValueResponse.from(service.updateValue(
                listId, valueId, request.label(), request.equivalence(), requestReader.permissions(httpRequest)));
    }

    @DeleteMapping("/{valueId}")
    ResponseEntity<Void> delete(
            @PathVariable String listId, @PathVariable String valueId, HttpServletRequest httpRequest) {
        service.deleteValue(listId, valueId, requestReader.user(httpRequest), requestReader.permissions(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
