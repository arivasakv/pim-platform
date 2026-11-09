package com.pim.backend.web;

import com.pim.backend.product.EanErrorCode;
import com.pim.backend.product.InvalidEanException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Central exception handler: every API error is RFC 9457 Problem Details with an {@code errors} list of
 * {@code {field, code, message}} (docs/decisions/0001-api-conventions.md). Ordered first so it takes
 * precedence over Spring Boot's default Problem Details handler for the exceptions it covers.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final String EAN_FIELD = "ean";
    private static final String INVALID_EAN_TITLE = "EAN no válido";
    private static final String INVALID_REQUEST_DETAIL = "La solicitud tiene datos no válidos.";

    /** One validation problem in a request. */
    public record FieldError(String field, String code, String message) {}

    @ExceptionHandler(InvalidEanException.class)
    ProblemDetail handleInvalidEan(InvalidEanException exception, HttpServletRequest request) {
        return invalidEan(request, List.of(eanError(exception.code())));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleMethodValidation(HandlerMethodValidationException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(ApiExceptionHandler::codeOf)
                .map(ApiExceptionHandler::eanError)
                .toList();
        return invalidEan(request, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> toEanErrorCode(violation.getMessage()))
                .map(ApiExceptionHandler::eanError)
                .toList();
        return invalidEan(request, errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return invalidEan(request, List.of(eanError(EanErrorCode.REQUIRED)));
    }

    private static ProblemDetail invalidEan(HttpServletRequest request, List<FieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL);
        problem.setTitle(INVALID_EAN_TITLE);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", errors);
        return problem;
    }

    private static FieldError eanError(EanErrorCode code) {
        return new FieldError(EAN_FIELD, code.name(), code.message());
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
