package com.pim.backend.product.controller;

import com.pim.backend.common.ApiFieldError;
import com.pim.backend.common.ApiProblems;
import com.pim.backend.product.model.EanErrorCode;
import com.pim.backend.product.model.InvalidEanException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Turns invalid-EAN errors from the product API into the project's Problem Details format (built by
 * {@link ApiProblems}). It applies only to {@link ProductController}, keeping EAN-specific error handling
 * in the product domain instead of in {@code common} (constitution XII). Ordered first so it takes
 * precedence over Spring Boot's default Problem Details handler for the exceptions it covers.
 */
@RestControllerAdvice(assignableTypes = ProductController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductExceptionHandler {

    private static final String EAN_FIELD = "ean";
    private static final String INVALID_EAN_TITLE = "EAN no válido";

    @ExceptionHandler(InvalidEanException.class)
    ProblemDetail handleInvalidEan(InvalidEanException exception, HttpServletRequest request) {
        return invalidEan(request, List.of(eanError(exception.code())));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(ProductExceptionHandler::codeOf)
                .map(ProductExceptionHandler::eanError)
                .toList();
        return invalidEan(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<ApiFieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> toEanErrorCode(violation.getMessage()))
                .map(ProductExceptionHandler::eanError)
                .toList();
        return invalidEan(request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return invalidEan(request, List.of(eanError(EanErrorCode.REQUIRED)));
    }

    private static ProblemDetail invalidEan(HttpServletRequest request, List<ApiFieldError> errors) {
        return ApiProblems.invalidRequest(request, INVALID_EAN_TITLE, errors);
    }

    private static ApiFieldError eanError(EanErrorCode code) {
        return new ApiFieldError(EAN_FIELD, code.name(), code.message());
    }

    private static EanErrorCode codeOf(MessageSourceResolvable error) {
        return toEanErrorCode(error.getDefaultMessage());
    }

    /** Bean Validation messages on the EAN parameter are error-code names (see ProductController). */
    private static EanErrorCode toEanErrorCode(String message) {
        for (EanErrorCode code : EanErrorCode.values()) {
            if (code.name().equals(message)) {
                return code;
            }
        }
        return EanErrorCode.INVALID_CHARACTERS;
    }
}
