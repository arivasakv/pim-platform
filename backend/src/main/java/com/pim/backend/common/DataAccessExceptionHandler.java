package com.pim.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers 503 Problem Details when a request needs a data store that cannot be reached, for example when
 * MongoDB is down (docs/decisions/0002-mongodb-product-catalog.md). Domain-neutral, so it lives in
 * {@code common} and applies to every controller (constitution XII). The client sees no internal details;
 * the cause is logged.
 */
@RestControllerAdvice
public class DataAccessExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DataAccessExceptionHandler.class);

    @ExceptionHandler(DataAccessResourceFailureException.class)
    ProblemDetail handleUnavailableStore(DataAccessResourceFailureException exception, HttpServletRequest request) {
        log.warn("Data store unavailable for {}: {}", request.getRequestURI(), exception.getMessage());
        return ApiProblems.serviceUnavailable(request);
    }
}
