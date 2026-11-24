package com.pim.backend.attribute.controller;

import com.pim.backend.attribute.model.AttributeChangeNotPermittedException;
import com.pim.backend.attribute.model.AttributeCreateNotPermittedException;
import com.pim.backend.attribute.model.AttributeErrorCode;
import com.pim.backend.attribute.model.AttributeInGroupsException;
import com.pim.backend.attribute.model.AttributeNotFoundException;
import com.pim.backend.attribute.model.AttributeNotListOfValuesException;
import com.pim.backend.attribute.model.AttributeOnTaxonomyNodesException;
import com.pim.backend.attribute.model.DisplayNameInUseException;
import com.pim.backend.attribute.model.ImmutableFieldException;
import com.pim.backend.attribute.model.InvalidAttributeException;
import com.pim.backend.attribute.model.ListOfValuesUnavailableException;
import com.pim.backend.attribute.model.UnlinkOutdatedException;
import com.pim.backend.attribute.model.UnlinkToListNotAllowedException;
import com.pim.backend.common.ApiFieldError;
import com.pim.backend.common.ApiProblems;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.SequenceExhaustedException;
import com.pim.backend.identifier.model.SystemIdInUseException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns attribute errors, and feature 003's errors met while creating attributes, into the project's Problem
 * Details format (built by {@link ApiProblems}; ADR 0001) with Spanish messages. It applies only to the controllers
 * of this package (constitution XII) and takes precedence over Spring Boot's default handler.
 */
@RestControllerAdvice(basePackageClasses = AttributeExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttributeExceptionHandler {

    static final String INVALID_REQUEST_TITLE = "Solicitud no válida";

    /** Feature 003's code for an invalid manual system ID. */
    private static final String INVALID_SYSTEM_ID = IdentifierErrorCode.INVALID_SYSTEM_ID.name();

    private static final String INVALID_SYSTEM_ID_MESSAGE = IdentifierErrorCode.INVALID_SYSTEM_ID.message();

    @ExceptionHandler(InvalidAttributeException.class)
    ProblemDetail handleInvalidAttribute(InvalidAttributeException exception, HttpServletRequest request) {
        return invalidRequest(request, List.of(error(exception.field(), exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(AttributeExceptionHandler::toApiFieldError)
                .toList();
        return invalidRequest(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return invalidRequest(
                request, List.of(error("body", AttributeErrorCode.REQUIRED, AttributeErrorCode.REQUIRED.message())));
    }

    @ExceptionHandler(AttributeCreateNotPermittedException.class)
    ProblemDetail handleNotPermitted(AttributeCreateNotPermittedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.FORBIDDEN,
                "Sin permiso para crear atributos",
                exception.getMessage(),
                List.of(error(
                        "permissions", AttributeErrorCode.ATTRIBUTE_CREATE_NOT_PERMITTED, exception.getMessage())));
    }

    @ExceptionHandler(AttributeChangeNotPermittedException.class)
    ProblemDetail handleChangeNotPermitted(AttributeChangeNotPermittedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.FORBIDDEN,
                "Sin permiso para modificar atributos",
                exception.getMessage(),
                List.of(error(
                        "permissions", AttributeErrorCode.ATTRIBUTE_CHANGE_NOT_PERMITTED, exception.getMessage())));
    }

    /** Feature 007 FR-010: the system ID and the data type never change. */
    @ExceptionHandler(ImmutableFieldException.class)
    ProblemDetail handleImmutableField(ImmutableFieldException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Cambio no permitido",
                exception.getMessage(),
                List.of(error(exception.field(), exception.code(), exception.getMessage())));
    }

    /** Feature 007 FR-014: the groups are also returned as the extension property {@code groups}. */
    @ExceptionHandler(AttributeInGroupsException.class)
    ProblemDetail handleInGroups(AttributeInGroupsException exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Atributo en grupos",
                exception.getMessage(),
                List.of(error("groups", AttributeErrorCode.ATTRIBUTE_IN_GROUPS, exception.getMessage())));
        problem.setProperty(
                "groups",
                exception.groups().stream()
                        .map(group -> Map.of("id", group.id(), "name", group.name()))
                        .toList());
        return problem;
    }

    /** Feature 010 FR-021: the nodes are also returned as the extension property {@code nodes}. */
    @ExceptionHandler(AttributeOnTaxonomyNodesException.class)
    ProblemDetail handleOnTaxonomyNodes(AttributeOnTaxonomyNodesException exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Atributo en la taxonomía",
                exception.getMessage(),
                List.of(error("nodes", AttributeErrorCode.ATTRIBUTE_ON_TAXONOMY_NODES, exception.getMessage())));
        problem.setProperty(
                "nodes",
                exception.nodes().stream()
                        .map(node -> Map.of("systemId", node.systemId(), "name", node.name()))
                        .toList());
        return problem;
    }

    // --- Feature 014: unlinking a list of values ---

    @ExceptionHandler(AttributeNotListOfValuesException.class)
    ProblemDetail handleNotListOfValues(AttributeNotListOfValuesException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Sin lista que desvincular",
                exception.getMessage(),
                List.of(error("dataType", AttributeErrorCode.ATTRIBUTE_NOT_LIST_OF_VALUES, exception.getMessage())));
    }

    @ExceptionHandler(UnlinkToListNotAllowedException.class)
    ProblemDetail handleUnlinkToList(UnlinkToListNotAllowedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Tipo de dato no permitido",
                exception.getMessage(),
                List.of(error("dataType", AttributeErrorCode.UNLINK_TO_LIST_NOT_ALLOWED, exception.getMessage())));
    }

    @ExceptionHandler(UnlinkOutdatedException.class)
    ProblemDetail handleUnlinkOutdated(UnlinkOutdatedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Atributo modificado",
                exception.getMessage(),
                List.of(error("reviewToken", AttributeErrorCode.UNLINK_OUTDATED, exception.getMessage())));
    }

    @ExceptionHandler(AttributeNotFoundException.class)
    ProblemDetail handleNotFound(AttributeNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "No encontrado",
                "No existe un atributo con el ID %s.".formatted(exception.requestedSystemId()),
                List.of());
    }

    @ExceptionHandler(DisplayNameInUseException.class)
    ProblemDetail handleDisplayNameInUse(DisplayNameInUseException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Nombre en uso",
                exception.getMessage(),
                List.of(error("displayName", AttributeErrorCode.DISPLAY_NAME_IN_USE, exception.getMessage())));
    }

    @ExceptionHandler(ListOfValuesUnavailableException.class)
    ProblemDetail handleListOfValuesUnavailable(
            ListOfValuesUnavailableException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Lista de valores no disponible",
                exception.getMessage(),
                List.of(error("listOfValuesProperties.listOfValuesId", exception.code(), exception.getMessage())));
    }

    // --- Feature 003's errors, passed on with its codes, titles and Spanish messages (FR-005) ---

    @ExceptionHandler(ManualIdNotPermittedException.class)
    ProblemDetail handleManualIdNotPermitted(ManualIdNotPermittedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.FORBIDDEN,
                "ID manual no permitido",
                ("No tienes permiso para definir manualmente el ID %s para este tipo de objeto. Envía la solicitud"
                                + " sin ID para que el sistema genere uno.")
                        .formatted(exception.requestedSystemId()),
                List.of(new ApiFieldError(
                        "systemId", IdentifierErrorCode.MANUAL_ID_NOT_PERMITTED.name(), exception.getMessage())));
    }

    @ExceptionHandler(SystemIdInUseException.class)
    ProblemDetail handleSystemIdInUse(SystemIdInUseException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "ID en uso",
                exception.getMessage(),
                List.of(new ApiFieldError(
                        "systemId", IdentifierErrorCode.SYSTEM_ID_IN_USE.name(), exception.getMessage())));
    }

    @ExceptionHandler(SequenceExhaustedException.class)
    ProblemDetail handleSequenceExhausted(SequenceExhaustedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Secuencia agotada",
                "La secuencia de IDs de este tipo de objeto llegó a su valor máximo.",
                List.of(new ApiFieldError(
                        "objectType", IdentifierErrorCode.SEQUENCE_EXHAUSTED.name(), exception.getMessage())));
    }

    @ExceptionHandler(InvalidIdentifierRequestException.class)
    ProblemDetail handleInvalidIdentifier(InvalidIdentifierRequestException exception, HttpServletRequest request) {
        return invalidRequest(
                request,
                List.of(new ApiFieldError(exception.field(), exception.code().name(), exception.getMessage())));
    }

    /**
     * The error code is chosen by field and constraint (tasks T017). A new constraint needs its own mapping here;
     * there is no silent fallback.
     */
    private static ApiFieldError toApiFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String constraint = fieldError.getCode();
        if ("NotNull".equals(constraint)) {
            return error(field, AttributeErrorCode.REQUIRED, AttributeErrorCode.REQUIRED.message());
        }
        if ("Size".equals(constraint) && "displayName".equals(field)) {
            return error(
                    field, AttributeErrorCode.INVALID_DISPLAY_NAME, AttributeErrorCode.INVALID_DISPLAY_NAME.message());
        }
        if ("Size".equals(constraint) && "systemId".equals(field)) {
            return new ApiFieldError(field, INVALID_SYSTEM_ID, INVALID_SYSTEM_ID_MESSAGE);
        }
        throw new IllegalStateException("No error code mapped for constraint " + constraint + " on " + field);
    }

    static ProblemDetail invalidRequest(HttpServletRequest request, List<ApiFieldError> errors) {
        return ApiProblems.invalidRequest(request, INVALID_REQUEST_TITLE, errors);
    }

    static ApiFieldError error(String field, AttributeErrorCode code, String message) {
        return new ApiFieldError(field, code.name(), message);
    }
}
