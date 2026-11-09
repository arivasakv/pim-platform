package com.pim.backend.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiProblemsTest {

    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/system-ids");

    @Test
    void buildsAProblemWithTheGivenStatusTitleDetailAndErrors() {
        ApiFieldError error =
                new ApiFieldError("systemId", "SYSTEM_ID_IN_USE", "El ID A-1 ya pertenece a otro objeto.");

        ProblemDetail problem =
                ApiProblems.problem(request, HttpStatus.CONFLICT, "ID en uso", "Detalle", List.of(error));

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getTitle()).isEqualTo("ID en uso");
        assertThat(problem.getDetail()).isEqualTo("Detalle");
        assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/system-ids"));
        assertThat(problem.getProperties()).containsEntry("errors", List.of(error));
    }

    @Test
    void leavesOutTheErrorsPropertyWhenThereAreNone() {
        ProblemDetail problem =
                ApiProblems.problem(request, HttpStatus.NOT_FOUND, "No encontrado", "Detalle", List.of());

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties()).isNullOrEmpty();
    }

    @Test
    void keepsTheInvalidRequestProblemUnchanged() {
        ApiFieldError error = new ApiFieldError("ean", "REQUIRED", "Escribe un EAN.");

        ProblemDetail problem = ApiProblems.invalidRequest(request, "EAN no válido", List.of(error));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).isEqualTo(ApiProblems.INVALID_REQUEST_DETAIL);
        assertThat(problem.getTitle()).isEqualTo("EAN no válido");
        assertThat(problem.getProperties()).containsEntry("errors", List.of(error));
    }
}
