package com.pim.backend.lov.controller;

import com.pim.backend.common.ApiFieldError;
import com.pim.backend.common.ApiProblems;
import com.pim.backend.identifier.model.IdentifierErrorCode;
import com.pim.backend.identifier.model.InvalidIdentifierRequestException;
import com.pim.backend.identifier.model.ManualIdNotPermittedException;
import com.pim.backend.identifier.model.SequenceExhaustedException;
import com.pim.backend.identifier.model.SystemIdInUseException;
import com.pim.backend.lov.model.ImportRowError;
import com.pim.backend.lov.model.InvalidImportException;
import com.pim.backend.lov.model.InvalidImportFileException;
import com.pim.backend.lov.model.InvalidListException;
import com.pim.backend.lov.model.ListChangeNotPermittedException;
import com.pim.backend.lov.model.ListInUseException;
import com.pim.backend.lov.model.ListNameInUseException;
import com.pim.backend.lov.model.ListNotFoundException;
import com.pim.backend.lov.model.ListTooLargeException;
import com.pim.backend.lov.model.LovErrorCode;
import com.pim.backend.lov.model.ValueIdInUseException;
import com.pim.backend.lov.model.ValueNotFoundException;
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
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * Turns list-of-values errors into the project's Problem Details format ({@link ApiProblems}; ADR 0001) with Spanish
 * messages and the codes of specs/006-lov-management/contracts/lists-of-values.openapi.yaml. It applies only to the
 * controllers of this package (constitution XII) and takes precedence over Spring Boot's default handler.
 *
 * <p>It maps <strong>only</strong> its own exceptions, feature 003's ID errors and request-format errors: there is no
 * handler for {@code Exception} or data-access errors, so an unreachable MongoDB still reaches {@code
 * common.DataAccessExceptionHandler} (503).
 */
@RestControllerAdvice(basePackageClasses = LovExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LovExceptionHandler {

    static final String INVALID_REQUEST_TITLE = "Solicitud no válida";

    private static final Map<String, LovErrorCode> SIZE_CODES = Map.of(
            "name", LovErrorCode.INVALID_LIST_NAME,
            "id", LovErrorCode.INVALID_VALUE_ID,
            "label", LovErrorCode.INVALID_LABEL,
            "equivalence", LovErrorCode.INVALID_EQUIVALENCE);

    @ExceptionHandler(InvalidListException.class)
    ProblemDetail handleInvalidList(InvalidListException exception, HttpServletRequest request) {
        return invalidRequest(request, List.of(error(exception.field(), exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(LovExceptionHandler::toApiFieldError)
                .toList();
        return invalidRequest(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return invalidRequest(request, List.of(error("body", LovErrorCode.REQUIRED, LovErrorCode.REQUIRED.message())));
    }

    @ExceptionHandler(ListChangeNotPermittedException.class)
    ProblemDetail handleNotPermitted(ListChangeNotPermittedException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.FORBIDDEN,
                "Sin permiso para administrar listas de valores",
                exception.getMessage(),
                "permissions",
                LovErrorCode.ATTRIBUTE_CREATE_NOT_PERMITTED);
    }

    @ExceptionHandler(ListNotFoundException.class)
    ProblemDetail handleListNotFound(ListNotFoundException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Lista no encontrada",
                exception.getMessage(),
                "listId",
                LovErrorCode.LIST_NOT_FOUND);
    }

    @ExceptionHandler(ValueNotFoundException.class)
    ProblemDetail handleValueNotFound(ValueNotFoundException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.NOT_FOUND,
                "Valor no encontrado",
                exception.getMessage(),
                "valueId",
                LovErrorCode.VALUE_NOT_FOUND);
    }

    @ExceptionHandler(ListNameInUseException.class)
    ProblemDetail handleNameInUse(ListNameInUseException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Nombre en uso",
                exception.getMessage(),
                "name",
                LovErrorCode.LIST_NAME_IN_USE);
    }

    @ExceptionHandler(ValueIdInUseException.class)
    ProblemDetail handleValueIdInUse(ValueIdInUseException exception, HttpServletRequest request) {
        return problem(
                request, HttpStatus.CONFLICT, "ID en uso", exception.getMessage(), "id", LovErrorCode.VALUE_ID_IN_USE);
    }

    @ExceptionHandler(ListInUseException.class)
    ProblemDetail handleListInUse(ListInUseException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.CONFLICT,
                "Lista en uso",
                exception.getMessage(),
                "listId",
                LovErrorCode.LIST_IN_USE);
    }

    @ExceptionHandler(ListTooLargeException.class)
    ProblemDetail handleTooLarge(ListTooLargeException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Lista demasiado grande",
                exception.getMessage(),
                "values",
                LovErrorCode.LIST_TOO_LARGE);
    }

    @ExceptionHandler(InvalidImportException.class)
    ProblemDetail handleInvalidImport(InvalidImportException exception, HttpServletRequest request) {
        ProblemDetail problem = problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Archivo con errores",
                exception.getMessage(),
                "file",
                LovErrorCode.INVALID_IMPORT);
        problem.setProperty(
                "rowErrors",
                exception.rowErrors().stream()
                        .map(LovExceptionHandler::rowError)
                        .toList());
        return problem;
    }

    @ExceptionHandler(InvalidImportFileException.class)
    ProblemDetail handleInvalidImportFile(InvalidImportFileException exception, HttpServletRequest request) {
        return problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Archivo no válido",
                exception.getMessage(),
                "file",
                exception.code());
    }

    /** No {@code file} part, or a body that is not multipart. */
    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    ProblemDetail handleMissingFile(HttpServletRequest request) {
        return invalidRequest(request, List.of(error("file", LovErrorCode.REQUIRED, LovErrorCode.REQUIRED.message())));
    }

    // --- Feature 003's errors for a list's system ID, mapped as feature 004 maps them ---

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
     * The error code is chosen by field and constraint. A new constraint needs its own mapping here; there is no
     * silent fallback.
     */
    private static ApiFieldError toApiFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String constraint = fieldError.getCode();
        if ("NotNull".equals(constraint)) {
            return error(field, LovErrorCode.REQUIRED, LovErrorCode.REQUIRED.message());
        }
        if ("Size".equals(constraint) && "systemId".equals(field)) {
            return new ApiFieldError(
                    field,
                    IdentifierErrorCode.INVALID_SYSTEM_ID.name(),
                    IdentifierErrorCode.INVALID_SYSTEM_ID.message());
        }
        LovErrorCode code = SIZE_CODES.get(field);
        if ("Size".equals(constraint) && code != null) {
            return error(field, code, code.message());
        }
        throw new IllegalStateException("No error code mapped for constraint " + constraint + " on " + field);
    }

    private static Map<String, Object> rowError(ImportRowError rowError) {
        return Map.of(
                "row", rowError.row(),
                "column", rowError.column(),
                "code", rowError.code().name(),
                "message", rowError.message());
    }

    private static ProblemDetail problem(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail,
            String field,
            LovErrorCode code) {
        return ApiProblems.problem(request, status, title, detail, List.of(error(field, code, detail)));
    }

    private static ProblemDetail invalidRequest(HttpServletRequest request, List<ApiFieldError> errors) {
        return ApiProblems.invalidRequest(request, INVALID_REQUEST_TITLE, errors);
    }

    private static ApiFieldError error(String field, LovErrorCode code, String message) {
        return new ApiFieldError(field, code.name(), message);
    }
}
