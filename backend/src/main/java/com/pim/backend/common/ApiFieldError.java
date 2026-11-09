package com.pim.backend.common;

/**
 * One validation problem in a request, listed in the {@code errors} property of an RFC 9457 Problem
 * Details response (docs/decisions/0001-api-conventions.md).
 *
 * @param field request field or parameter name
 * @param code stable, machine-readable error code
 * @param message Spanish text for the user
 */
public record ApiFieldError(String field, String code, String message) {}
