package com.pim.backend.attributegroup.controller;

import com.pim.backend.attributegroup.model.AttributeGroupErrorCode;
import com.pim.backend.attributegroup.model.AttributeNotInGroupException;
import com.pim.backend.attributegroup.model.GroupChangeNotPermittedException;
import com.pim.backend.attributegroup.model.GroupNameInUseException;
import com.pim.backend.attributegroup.model.GroupNotEmptyException;
import com.pim.backend.attributegroup.model.GroupNotFoundException;
import com.pim.backend.attributegroup.model.InvalidGroupException;
import com.pim.backend.attributegroup.model.UnknownAttributeException;
import com.pim.backend.common.ApiFieldError;
import com.pim.backend.common.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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
 * Turns group errors into the project's Problem Details format (built by {@link ApiProblems}; ADR 0001) with Spanish
 * messages. It applies only to the controllers of this package (constitution XII) and takes precedence over Spring
 * Boot's default handler.
 *
 * <p>It maps <strong>only</strong> its own exceptions and request-format errors: there is no handler for {@code
 * Exception}, {@code RuntimeException} or data-access errors, so an unreachable MongoDB still reaches {@code
 * common.DataAccessExceptionHandler} (503).
 */
@RestControllerAdvice(basePackageClasses = AttributeGroupExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttributeGroupExceptionHandler {

    static final String INVALID_REQUEST_TITLE = "Solicitud no válida";

    @ExceptionHandler(InvalidGroupException.class)
    ProblemDetail handleInvalidGroup(InvalidGroupException exception, HttpServletRequest request) {
        return invalidRequest(request, List.of(error(exception.field(), exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(AttributeGroupExceptionHandler::toApiFieldError)
                .toList();
        return invalidRequest(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return invalidRequest(request, List.of(error("body", AttributeGroupErrorCode.REQUIRED)));
    }

    @ExceptionHandler(GroupChangeNotPermittedException.class)
    ProblemDetail handleNotPermitted(GroupChangeNotPermittedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.FORBIDDEN,
                "Sin permiso para administrar grupos",
                exception.getMessage(),
                List.of(error(
                        "permissions",
                        AttributeGroupErrorCode.ATTRIBUTE_CREATE_NOT_PERMITTED,
                        exception.getMessage())));
    }

    @ExceptionHandler(GroupNotFoundException.class)
    ProblemDetail handleGroupNotFound(GroupNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "Grupo no encontrado",
                exception.getMessage(),
                List.of(error("groupId", AttributeGroupErrorCode.GROUP_NOT_FOUND, exception.getMessage())));
    }

    @ExceptionHandler(AttributeNotInGroupException.class)
    ProblemDetail handleNotInGroup(AttributeNotInGroupException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "Atributo fuera del grupo",
                exception.getMessage(),
                List.of(error("attributeId", AttributeGroupErrorCode.ATTRIBUTE_NOT_IN_GROUP, exception.getMessage())));
    }

    @ExceptionHandler(GroupNameInUseException.class)
    ProblemDetail handleNameInUse(GroupNameInUseException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Nombre en uso",
                exception.getMessage(),
                List.of(error("name", AttributeGroupErrorCode.GROUP_NAME_IN_USE, exception.getMessage())));
    }

    @ExceptionHandler(GroupNotEmptyException.class)
    ProblemDetail handleNotEmpty(GroupNotEmptyException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Grupo con atributos",
                exception.getMessage(),
                List.of(error("groupId", AttributeGroupErrorCode.GROUP_NOT_EMPTY, exception.getMessage())));
    }

    @ExceptionHandler(UnknownAttributeException.class)
    ProblemDetail handleUnknownAttribute(UnknownAttributeException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Atributo no encontrado",
                exception.getMessage(),
                List.of(error("attributeIds", AttributeGroupErrorCode.ATTRIBUTE_NOT_FOUND, exception.getMessage())));
    }

    /**
     * The error code is chosen by field and constraint (tasks T023). A new constraint needs its own mapping here;
     * there is no silent fallback.
     */
    private static ApiFieldError toApiFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String constraint = fieldError.getCode();
        if ("NotNull".equals(constraint)) {
            return error(field, AttributeGroupErrorCode.REQUIRED);
        }
        if ("Size".equals(constraint) && "name".equals(field)) {
            return error(field, AttributeGroupErrorCode.INVALID_GROUP_NAME);
        }
        if ("Size".equals(constraint) && "description".equals(field)) {
            return error(field, AttributeGroupErrorCode.INVALID_GROUP_DESCRIPTION);
        }
        if ("Size".equals(constraint) && "attributeIds".equals(field)) {
            return error(field, AttributeGroupErrorCode.INVALID_ATTRIBUTE_IDS);
        }
        throw new IllegalStateException("No error code mapped for constraint " + constraint + " on " + field);
    }

    private static ProblemDetail invalidRequest(HttpServletRequest request, List<ApiFieldError> errors) {
        return ApiProblems.invalidRequest(request, INVALID_REQUEST_TITLE, errors);
    }

    private static ApiFieldError error(String field, AttributeGroupErrorCode code) {
        return error(field, code, code.message());
    }

    private static ApiFieldError error(String field, AttributeGroupErrorCode code, String message) {
        return new ApiFieldError(field, code.name(), message);
    }
}
