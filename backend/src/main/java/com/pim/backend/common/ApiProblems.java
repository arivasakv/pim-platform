package com.pim.backend.common;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Builds the project's single error format: RFC 9457 Problem Details with an {@code errors} list
 * (docs/decisions/0001-api-conventions.md; constitution VII). Exception handlers use it so every
 * endpoint answers errors the same way.
 */
public final class ApiProblems {

    /** Detail text shared by every invalid-request problem. */
    public static final String INVALID_REQUEST_DETAIL = "La solicitud tiene datos no válidos.";

    /** Title of the problem returned when a data store cannot be reached. */
    public static final String SERVICE_UNAVAILABLE_TITLE = "Servicio no disponible";

    /** Detail of the problem returned when a data store cannot be reached. */
    public static final String SERVICE_UNAVAILABLE_DETAIL =
            "No se pudieron consultar los datos en este momento. Inténtalo de nuevo más tarde.";

    private ApiProblems() {}

    /** A 400 problem with the given title and field errors; {@code instance} is the request path. */
    public static ProblemDetail invalidRequest(HttpServletRequest request, String title, List<ApiFieldError> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_REQUEST_DETAIL);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errors", List.copyOf(errors));
        return problem;
    }

    /**
     * A 503 problem for a request that needs a data store that cannot be reached
     * (docs/decisions/0002-mongodb-product-catalog.md); {@code instance} is the request path. It has no
     * {@code errors}, because the request itself was valid.
     */
    public static ProblemDetail serviceUnavailable(HttpServletRequest request) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, SERVICE_UNAVAILABLE_DETAIL);
        problem.setTitle(SERVICE_UNAVAILABLE_TITLE);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
