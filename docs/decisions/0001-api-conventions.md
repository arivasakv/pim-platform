# ADR 0001: API error format and collection envelope

- **Status**: Accepted
- **Date**: 2026-11-08
- **Approved by**: the project owner
- **Origin**: feature 002, plan decision D-4
  ([plan.md](../../specs/002-existing-product-search/plan.md),
  [research.md](../../specs/002-existing-product-search/research.md) R-07)

## Context

Constitution Principle VII requires:
- a single, consistent error format across all endpoints (RFC 7807 Problem Details recommended),
  produced by a centralized exception handler;
- DTOs with camelCase fields;
- a consistent envelope for collections;
- correct status codes.

The existing `GET /api/v1/hello` returns plain text and defines no convention. Feature 002 adds the
first real endpoint, `GET /api/v1/products?ean=…`, so the convention is decided now and applies to
every later endpoint.

## Decision

### 1. Errors: RFC 9457 Problem Details

RFC 9457 is the current version of RFC 7807 and is compatible with it.

- Every error response uses the media type `application/problem+json`, with these fields:

  | Field | Meaning |
  |---|---|
  | `type` | URI identifying the problem type. Omitted when it is `about:blank`, the default (RFC 9457 §3.1.1); clients treat a missing `type` as `about:blank` |
  | `title` | Short, human-readable summary, in Spanish (for example "EAN no válido") |
  | `status` | HTTP status code |
  | `detail` | Human-readable explanation of this occurrence, in Spanish |
  | `instance` | Request path |
  | `errors` | For validation errors: a list of `{ "field", "code", "message" }`. `code` is stable and machine-readable (for example `INVALID_LENGTH`); `message` is Spanish text for the user |

- Errors are produced by **one** `@RestControllerAdvice` (`com.pim.backend.web.ApiExceptionHandler`),
  using Spring's `ProblemDetail`. Controllers do not build error bodies.
- `spring.mvc.problemdetails.enabled: true` makes framework errors (for example, an unknown route or
  an unsupported method) use the same format.
- Clients rely on `status` and `errors[].code`, never on the wording of `title`, `detail` or
  `message`.

Example:

```json
{
  "title": "EAN no válido",
  "status": 400,
  "detail": "La solicitud tiene datos no válidos.",
  "instance": "/api/v1/products",
  "errors": [
    { "field": "ean", "code": "INVALID_LENGTH", "message": "El EAN debe tener 8, 12, 13 o 14 dígitos." }
  ]
}
```

### 2. Collections: `{ items, total }` envelope

- Every response that returns a list uses `{ "items": [ … ], "total": n }`, where `total` is the
  number of matching elements.
- A filtered collection with no match returns **200** with `{ "items": [], "total": 0 }`, never 404.
  404 is reserved for a specific resource that does not exist (for example `/api/v1/products/{sku}`).
- Pagination fields, when a later feature needs them, are added next to `items` and `total` without
  changing these two.

### 3. Status codes

The codes follow constitution VII:
- 200, 201 and 204 for success;
- 400 for invalid input (including missing or malformed parameters);
- 404 for a missing resource;
- 409 for conflicts;
- 422 for business-rule violations where applicable.

## Consequences

- The frontend can handle every error the same way: read `status`, then `errors[].code`, and show
  its own Spanish text for each code. This keeps the UI's wording under the frontend's control.
- Each new endpoint documents its Problem responses and error codes in its OpenAPI contract
  (constitution VII), reusing the `Problem` and collection schemas from
  `specs/002-existing-product-search/contracts/product-search.openapi.yaml`.
- The existing `GET /api/v1/hello` (plain text) is not changed by this ADR. If it is ever
  extended, it adopts these conventions.

## Alternatives considered

- **Custom error JSON** (for example `{ "error": "...", "message": "..." }`): simple, but
  non-standard, and every client would have to learn it. Rejected.
- **Plain-text error bodies**: not machine-readable; clients cannot map errors to fields. Rejected.
- **A bare JSON array for collections**: cannot grow to carry `total` or pagination later without a
  breaking change. Rejected.
- **404 for a filter with no match**: confuses "the resource type does not exist" with "nothing
  matched". Rejected.
