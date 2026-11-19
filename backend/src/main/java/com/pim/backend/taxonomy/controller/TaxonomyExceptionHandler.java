package com.pim.backend.taxonomy.controller;

import com.pim.backend.common.ApiFieldError;
import com.pim.backend.common.ApiProblems;
import com.pim.backend.taxonomy.model.AttributeNotDirectException;
import com.pim.backend.taxonomy.model.AttributeNotListException;
import com.pim.backend.taxonomy.model.AttributeNotOnNodeException;
import com.pim.backend.taxonomy.model.FilterAttributeNotFoundException;
import com.pim.backend.taxonomy.model.FilterNotFoundException;
import com.pim.backend.taxonomy.model.InvalidFilterException;
import com.pim.backend.taxonomy.model.InvalidNodeException;
import com.pim.backend.taxonomy.model.NodeMoveNotAllowedException;
import com.pim.backend.taxonomy.model.NodeNameInUseException;
import com.pim.backend.taxonomy.model.NodeNotEmptyException;
import com.pim.backend.taxonomy.model.NodeNotFoundException;
import com.pim.backend.taxonomy.model.TaxonomyChangeNotPermittedException;
import com.pim.backend.taxonomy.model.TaxonomyErrorCode;
import com.pim.backend.taxonomy.model.UnknownAttributeException;
import com.pim.backend.taxonomy.model.ValueExcludedAboveException;
import com.pim.backend.taxonomy.model.ValueNotInListException;
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
 * Turns taxonomy errors into the project's Problem Details format ({@link ApiProblems}; ADR 0001) with Spanish
 * messages. It applies only to the controllers of this package (constitution XII). It maps only its own exceptions
 * and request-format errors, so an unreachable MongoDB still reaches {@code common.DataAccessExceptionHandler} (503).
 */
@RestControllerAdvice(basePackageClasses = TaxonomyExceptionHandler.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TaxonomyExceptionHandler {

    static final String INVALID_REQUEST_TITLE = "Solicitud no válida";

    @ExceptionHandler(InvalidNodeException.class)
    ProblemDetail handleInvalidNode(InvalidNodeException exception, HttpServletRequest request) {
        return invalidRequest(request, List.of(error(exception.field(), exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBeanValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(TaxonomyExceptionHandler::toApiFieldError)
                .toList();
        return invalidRequest(request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return invalidRequest(request, List.of(error("body", TaxonomyErrorCode.REQUIRED)));
    }

    @ExceptionHandler(TaxonomyChangeNotPermittedException.class)
    ProblemDetail handleNotPermitted(TaxonomyChangeNotPermittedException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.FORBIDDEN,
                "Sin permiso para administrar la taxonomía",
                exception.getMessage(),
                List.of(error("permissions", TaxonomyErrorCode.TAXONOMY_CHANGE_NOT_PERMITTED, exception.getMessage())));
    }

    @ExceptionHandler(NodeNotFoundException.class)
    ProblemDetail handleNodeNotFound(NodeNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "Nodo no encontrado",
                exception.getMessage(),
                List.of(error(exception.field(), TaxonomyErrorCode.NODE_NOT_FOUND, exception.getMessage())));
    }

    @ExceptionHandler(AttributeNotOnNodeException.class)
    ProblemDetail handleNotOnNode(AttributeNotOnNodeException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "Atributo fuera del nodo",
                exception.getMessage(),
                List.of(error("attributeId", TaxonomyErrorCode.ATTRIBUTE_NOT_ON_NODE, exception.getMessage())));
    }

    @ExceptionHandler(NodeNameInUseException.class)
    ProblemDetail handleNameInUse(NodeNameInUseException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Nombre en uso",
                exception.getMessage(),
                List.of(error(exception.field(), TaxonomyErrorCode.NODE_NAME_IN_USE, exception.getMessage())));
    }

    /** Feature 011: a move into the node's own branch, or to where it already is. */
    @ExceptionHandler(NodeMoveNotAllowedException.class)
    ProblemDetail handleMoveNotAllowed(NodeMoveNotAllowedException exception, HttpServletRequest request) {
        String title = exception.code() == TaxonomyErrorCode.NODE_MOVE_INTO_OWN_BRANCH
                ? "Movimiento no válido"
                : "Movimiento sin efecto";
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                title,
                exception.getMessage(),
                List.of(error("parentId", exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(NodeNotEmptyException.class)
    ProblemDetail handleNotEmpty(NodeNotEmptyException exception, HttpServletRequest request) {
        String title =
                exception.code() == TaxonomyErrorCode.NODE_HAS_CHILDREN ? "Nodo con nodos hijos" : "Nodo con atributos";
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                title,
                exception.getMessage(),
                List.of(error("nodeId", exception.code(), exception.getMessage())));
    }

    @ExceptionHandler(AttributeNotDirectException.class)
    ProblemDetail handleNotDirect(AttributeNotDirectException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Atributo heredado",
                exception.getMessage(),
                List.of(error("attributeId", TaxonomyErrorCode.ATTRIBUTE_NOT_DIRECT, exception.getMessage())));
    }

    @ExceptionHandler(UnknownAttributeException.class)
    ProblemDetail handleUnknownAttribute(UnknownAttributeException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Atributo no encontrado",
                exception.getMessage(),
                List.of(error("attributeIds", TaxonomyErrorCode.ATTRIBUTE_NOT_FOUND, exception.getMessage())));
    }

    // --- Feature 012: list-value filters ---

    @ExceptionHandler(InvalidFilterException.class)
    ProblemDetail handleInvalidFilter(InvalidFilterException exception, HttpServletRequest request) {
        return invalidRequest(
                request, List.of(error("valueIds", TaxonomyErrorCode.INVALID_FILTER_VALUES, exception.getMessage())));
    }

    @ExceptionHandler(FilterAttributeNotFoundException.class)
    ProblemDetail handleFilterAttributeNotFound(
            FilterAttributeNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "Atributo no encontrado",
                exception.getMessage(),
                List.of(error("attributeId", TaxonomyErrorCode.ATTRIBUTE_NOT_FOUND, exception.getMessage())));
    }

    @ExceptionHandler(AttributeNotListException.class)
    ProblemDetail handleNotList(AttributeNotListException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Atributo sin lista de valores",
                exception.getMessage(),
                List.of(error("attributeId", TaxonomyErrorCode.ATTRIBUTE_NOT_LIST, exception.getMessage())));
    }

    @ExceptionHandler(ValueNotInListException.class)
    ProblemDetail handleNotInList(ValueNotInListException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.UNPROCESSABLE_CONTENT,
                "Valor no encontrado",
                exception.getMessage(),
                List.of(error("valueIds", TaxonomyErrorCode.VALUE_NOT_IN_LIST, exception.getMessage())));
    }

    @ExceptionHandler(ValueExcludedAboveException.class)
    ProblemDetail handleExcludedAbove(ValueExcludedAboveException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.CONFLICT,
                "Valor excluido arriba",
                exception.getMessage(),
                List.of(error("valueIds", TaxonomyErrorCode.VALUE_EXCLUDED_ABOVE, exception.getMessage())));
    }

    @ExceptionHandler(FilterNotFoundException.class)
    ProblemDetail handleFilterNotFound(FilterNotFoundException exception, HttpServletRequest request) {
        return ApiProblems.problem(
                request,
                HttpStatus.NOT_FOUND,
                "Filtro no encontrado",
                exception.getMessage(),
                List.of(error("attributeId", TaxonomyErrorCode.FILTER_NOT_FOUND, exception.getMessage())));
    }

    /** The error code is chosen by field and constraint; a new constraint needs its own mapping here. */
    private static ApiFieldError toApiFieldError(FieldError fieldError) {
        String field = fieldError.getField();
        String constraint = fieldError.getCode();
        if ("NotNull".equals(constraint)) {
            return error(field, TaxonomyErrorCode.REQUIRED);
        }
        if ("Size".equals(constraint) && "name".equals(field)) {
            return error(field, TaxonomyErrorCode.INVALID_NODE_NAME);
        }
        if ("Size".equals(constraint) && "description".equals(field)) {
            return error(field, TaxonomyErrorCode.INVALID_NODE_DESCRIPTION);
        }
        if ("Size".equals(constraint) && "parentId".equals(field)) {
            return error(field, TaxonomyErrorCode.NODE_NOT_FOUND, "El ID del nodo padre no es válido.");
        }
        if (field.startsWith("valueIds")) {
            return error("valueIds", TaxonomyErrorCode.INVALID_FILTER_VALUES);
        }
        if ("Size".equals(constraint) && field.startsWith("attributeIds")) {
            return error("attributeIds", TaxonomyErrorCode.INVALID_ATTRIBUTE_IDS);
        }
        throw new IllegalStateException("No error code mapped for constraint " + constraint + " on " + field);
    }

    private static ProblemDetail invalidRequest(HttpServletRequest request, List<ApiFieldError> errors) {
        return ApiProblems.invalidRequest(request, INVALID_REQUEST_TITLE, errors);
    }

    private static ApiFieldError error(String field, TaxonomyErrorCode code) {
        return error(field, code, code.message());
    }

    private static ApiFieldError error(String field, TaxonomyErrorCode code, String message) {
        return new ApiFieldError(field, code.name(), message);
    }
}
