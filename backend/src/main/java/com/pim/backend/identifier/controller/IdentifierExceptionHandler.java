package com.pim.backend.identifier.controller;

import com.pim.backend.common.ApiFieldError;
import com.pim.backend.common.ApiProblems;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.ObjectTypeNotFoundException;
import com.pim.backend.identifier.model.SequenceExhaustedException;
import com.pim.backend.identifier.model.SystemIdImmutableException;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.identifier.model.SystemIdNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns identifier errors into the project's Problem Details format (built by {@link ApiProblems}; ADR 0001,
 * research R-11), with Spanish messages (FR-011). It applies only to the controllers of this package, keeping
 * identifier messages in the identifier domain (constitution XII), and takes precedence over Spring Boot's default
 * Problem Details handler.
 */
@RestControllerAdvice(basePackageClasses = IdentifierExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IdentifierExceptionHandler {

    private static final String INVALID_REQUEST_TITLE = "Solicitud no válida";
    private static final String NOT_FOUND_TITLE = "No encontrado";
    private static final String TOO_MANY_KEY_FIELDS_MESSAGE = "Se enviaron más de 20 campos llave.";

    @ExceptionHandler(InvalidIdentifierRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidIdentifierRequestException exception, HttpServletRequest request) {
        return invalidRequest(request, List.of(error(exception.field(), exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(IdentifierExceptionHandler::toApiFieldError)
                .toList();
        return invalidRequest(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return invalidRequest(
                request, List.of(error("body", IdentifierErrorCode.REQUIRED, IdentifierErrorCode.REQUIRED.message())));
    }

    @ExceptionHandler(SystemIdNotFoundException.class)
    ProblemDetail handleSystemIdNotFound(SystemIdNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                NOT_FOUND_TITLE,
                "No existe un objeto con el ID %s.".formatted(exception.requestedSystemId()),
                List.of());
    }

    @ExceptionHandler(ObjectTypeNotFoundException.class)
    ProblemDetail handleObjectTypeNotFound(ObjectTypeNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                NOT_FOUND_TITLE,
                "No existe el tipo de objeto %s.".formatted(exception.requestedObjectType()),
                List.of());
    }

    @ExceptionHandler(SequenceExhaustedException.class)
    ProblemDetail handleSequenceExhausted(SequenceExhaustedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Secuencia agotada",
                "La secuencia de IDs de este tipo de objeto llegó a su valor máximo.",
                List.of(error("objectType", IdentifierErrorCode.SEQUENCE_EXHAUSTED, exception.getMessage())));
    }

    @ExceptionHandler(ManualIdNotPermittedException.class)
    ProblemDetail handleManualIdNotPermitted(ManualIdNotPermittedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.FORBIDDEN,
                "ID manual no permitido",
                ("No tienes permiso para definir manualmente el ID %s para este tipo de objeto. Envía la solicitud"
                                + " sin ID para que el sistema genere uno.")
                        .formatted(exception.requestedSystemId()),
                List.of(error("systemId", IdentifierErrorCode.MANUAL_ID_NOT_PERMITTED, exception.getMessage())));
    }

    @ExceptionHandler(SystemIdInUseException.class)
    ProblemDetail handleSystemIdInUse(SystemIdInUseException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "ID en uso",
                exception.getMessage(),
                List.of(error("systemId", IdentifierErrorCode.SYSTEM_ID_IN_USE, exception.getMessage())));
    }

    @ExceptionHandler(SystemIdImmutableException.class)
    ResponseEntity<ProblemDetail> handleSystemIdImmutable(
            SystemIdImmutableException exception, HttpServletRequest request) {
        ProblemDetail problem = ApiProblems.problem(
                request,
                HttpStatus.METHOD_NOT_ALLOWED,
                "ID no modificable",
                "El ID de sistema %s no se puede modificar una vez guardado.".formatted(exception.requestedSystemId()),
                List.of());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(HttpMethod.GET)
                .body(problem);
    }

    /**
     * The error code is chosen by field and constraint (tasks T023). A new constraint needs its own mapping here;
     * there is no silent fallback.
     */
    private static ApiFieldError toApiFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String constraint = fieldError.getCode();
        if ("NotNull".equals(constraint)) {
            return error(field, IdentifierErrorCode.REQUIRED, IdentifierErrorCode.REQUIRED.message());
        }
        if ("Size".equals(constraint) && "systemId".equals(field)) {
            return error(field, IdentifierErrorCode.INVALID_SYSTEM_ID, IdentifierErrorCode.INVALID_SYSTEM_ID.message());
        }
        if ("Size".equals(constraint) && "keyFieldValues".equals(field)) {
            return error(field, IdentifierErrorCode.UNKNOWN_KEY_FIELD, TOO_MANY_KEY_FIELDS_MESSAGE);
        }
        throw new IllegalStateException("No error code mapped for constraint " + constraint + " on " + field);
    }

    private static ProblemDetail invalidRequest(HttpServletRequest request, List<ApiFieldError> errors) {
        return ApiProblems.invalidRequest(request, INVALID_REQUEST_TITLE, errors);
    }

    static ApiFieldError error(String field, IdentifierErrorCode code, String message) {
        return new ApiFieldError(field, code.name(), message);
    }
}
