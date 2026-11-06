<!--
Sync Impact Report
==================
Version change: (template, unversioned) → 1.0.0
Bump rationale: Initial ratification. All template placeholders replaced with project content.

Modified principles (template slot → new title):
  - [PRINCIPLE_1_NAME] → I. Specification-Driven Development (NON-NEGOTIABLE)
  - [PRINCIPLE_2_NAME] → II. Technology Stack
  - [PRINCIPLE_3_NAME] → III. Architecture: Separated Apps & Modular Monolith
  - [PRINCIPLE_4_NAME] → IV. PIM Business Domains
  - [PRINCIPLE_5_NAME] → V. Code Quality
Added principles (beyond the template's five slots):
  - VI. Testing (NON-NEGOTIABLE)
  - VII. API Design
  - VIII. Security
  - IX. Development Scope
  - X. AI Development Governance
Added sections:
  - Quality Gates & Definition of Done (template SECTION_2)
  - Development Workflow (template SECTION_3)
  - Governance (filled)
Removed sections: none

Templates: not modified (dependent templates and commands read this file at runtime).
Deferred TODOs: none.
-->

# PIM Platform Constitution

## Core Principles

### I. Specification-Driven Development (NON-NEGOTIABLE)

- Every feature MUST have an approved `spec.md` before any implementation task starts.
- Work MUST follow the Spec Kit workflow in order: Specify → Clarify → Plan → Tasks →
  Implement → Analyze. Steps MAY be repeated but MUST NOT be skipped.
- Code MUST NOT implement behavior that is not traceable to a requirement or acceptance
  scenario in an approved specification. Every task in `tasks.md` MUST reference the
  requirement(s) it fulfills.
- Ambiguities that affect scope, data model, API contracts, validation rules, or user-visible
  behavior MUST be resolved (via `/speckit-clarify` or a recorded decision) before `/speckit-plan`
  is finalized. No `[NEEDS CLARIFICATION]` markers may remain in a spec entering implementation.
- Specification, implementation, and tests MUST stay aligned: when behavior changes, the spec is
  updated first, then the tests, then the code. `/speckit-analyze` MUST report no
  CRITICAL inconsistencies before a feature is declared complete.

**Rationale**: The spec is the single source of truth; code that is not specified cannot be
reviewed, tested, or accepted against an agreed expectation.

### II. Technology Stack

- **Frontend**: React with TypeScript, built with Vite. TypeScript `strict` mode MUST be
  enabled; plain `.js`/`.jsx` source files MUST NOT be added.
- **Backend**: Java 21, Spring Boot, built with Gradle. Communication is via REST APIs (JSON).
- Introducing another language, framework, build tool, or runtime (e.g., Gradle, Kotlin,
  GraphQL, a state-management or UI library not already adopted) REQUIRES a recorded
  architectural decision approved in a plan's Complexity Tracking or an ADR.

**Rationale**: A fixed, well-known stack keeps the codebase consistent and reviewable and stops
tooling from sprawling.

### III. Architecture: Separated Apps & Modular Monolith

- Frontend and backend MUST be separate applications with separate build files and MUST be
  independently buildable and runnable.
- The frontend MUST communicate with the backend only through its documented REST API; it MUST
  NOT share code, data files, or in-process state with the backend.
- The backend MUST be a single deployable modular monolith. Code MUST be organized by
  business domain (package per domain, e.g. `...template`, `...attribute`, `...lov`), not by
  technical layer at the top level.
- A domain module MUST NOT access another module's internal classes or data stores directly; it
  MUST use that module's public service interface.
- Controllers MUST only handle HTTP concerns (mapping, request validation, status codes) and
  MUST delegate business logic to service/domain classes. Business rules in a controller are a
  review-blocking defect.
- Microservices, message brokers, or splitting the backend into multiple deployables MUST NOT be
  introduced without an approved architectural decision record.

**Rationale**: A modular monolith with clear domain boundaries gives the simplest operating model
today while keeping a later extraction path open.

### IV. PIM Business Domains
<!--
The initial scope of the application is limited to these business domains:

1. **Template Management** — create, read, update, and retire product templates.
2. **Attribute Management** — define attributes, their data types, and constraints.
3. **List of Values (LOV) Management** — manage controlled value lists used by attributes.
4. **Template Composition** — assign attributes (and their LOVs) to templates.
5. **Template Configuration and Validation** — configure template rules and validate
   templates and their composition against them.

- Each domain MUST map to its own backend module and MUST be named consistently (same term)
  across specs, API paths, code packages, and UI labels.
- Features outside these domains MUST be introduced through a new specification and, if they add
  a domain, an amendment to this list.

**Rationale**: A bounded initial scope and a shared vocabulary prevent scope creep and naming drift.
-->

### V. Code Quality

- Code MUST follow established conventions: React function components and hooks; idiomatic
  TypeScript; standard Java and Spring Boot conventions (constructor injection, no field
  injection).
- Names of classes, methods, components, variables, and endpoints MUST express domain intent;
  abbreviations other than well-known ones (e.g. LOV, API, DTO) MUST NOT be used.
- Each class and component MUST have a single, clearly stated responsibility. Duplicated logic
  MUST be consolidated rather than copied.
- New dependencies MUST be justified in the feature's `plan.md`; unused dependencies MUST be
  removed.
- Linting/formatting (ESLint + Prettier for frontend; a configured Java formatter/static checks
  for backend) MUST pass with zero errors.

**Rationale**: Readable, focused code lowers the cost of every later change and review.

### VI. Testing (NON-NEGOTIABLE)

- Every feature MUST include unit tests for its business logic (backend services/domain classes
  and frontend logic/components).
- Every backend endpoint MUST have API integration tests (e.g. Spring `@SpringBootTest` with
  MockMvc/WebTestClient) covering the success path and at least one error path.
- Every validation rule in a specification MUST have at least one test asserting its accepted
  and rejected cases, including the expected HTTP status and error payload.
- A feature is NOT complete until all of its tests, and the full existing test suite, pass in a
  clean build (`mvn verify`, frontend test script).

**Rationale**: Tests are the executable proof that implementation matches specification.

### VII. API Design

- APIs MUST follow RESTful conventions: plural resource nouns, HTTP verbs for actions, and
  hierarchical paths for owned sub-resources, under a versioned base path (e.g. `/api/v1/...`).
- Request and response bodies MUST use DTOs, never domain/internal objects, with consistent
  field naming (camelCase) and a consistent envelope for collections.
- Errors MUST use a single, consistent error format across all endpoints (RFC 7807 Problem
  Details recommended), produced by a centralized exception handler.
- Incoming requests MUST be validated on the backend (Bean Validation plus domain rules).
- Status codes MUST be correct: 200/201/204 for success, 400 for invalid input, 404 for missing
  resources, 409 for conflicts, 422 for business-rule violations where applicable.
- Every endpoint MUST be documented in an API contract (OpenAPI) that is created in the feature's
  `contracts/` during planning and kept in sync with the implementation.

**Rationale**: Predictable, documented contracts let the frontend and backend evolve independently.

### VIII. Security

- Secrets, credentials, tokens, and private keys MUST NOT appear in frontend code, in any source
  file, or in version control.
- Environment-specific configuration MUST live outside source code (environment variables,
  externalized Spring profiles/properties, Vite `.env` files excluded from VCS). Only
  non-sensitive values MAY be exposed to the frontend bundle.
- Backend validation is authoritative. Frontend validation exists for user experience only and
  MUST NOT replace any backend check.
- APIs MUST be designed so authentication and authorization can be added later without breaking
  contracts (e.g. no user identity in paths/bodies where it belongs in auth context; a central
  place for security configuration).

**Rationale**: Security choices that are cheap now are expensive to retrofit later.

### IX. Development Scope

Until this constitution is amended:

- The backend MUST use in-memory mock data (e.g. seeded repositories behind repository
  interfaces). Persistence MUST be accessed only through interfaces so it can be replaced later.
- Database persistence (JPA, JDBC, migrations, embedded or external databases) MUST NOT be
  introduced.
- Real external integrations (commerce platforms, ERPs, DAMs, messaging, third-party APIs) MUST
  NOT be implemented; integration points MAY be represented as interfaces only if a spec requires
  them.
- AI-powered application features MUST NOT be implemented.
- Each of these future capabilities MUST be introduced through its own specification and, when
  it lifts a restriction above, a constitution amendment.

**Rationale**: A deliberately narrow scope lets the domain model and UX stabilize before
infrastructure commitments are made.

### X. AI Development Governance

AI agents and human contributors MUST:

- Explain significant architectural decisions (new module, pattern, dependency, or contract change)
  in `plan.md` or an ADR, including alternatives considered.
- Work in small, reviewable increments: one task (or a tightly related group) per change, each
  leaving the build green.
- Never silently change an approved specification; changes to `spec.md` MUST be explicit,
  explained, and re-approved.
- Ask for clarification instead of guessing when requirements are ambiguous.
- Not modify files, behavior, or tests unrelated to the current task.

**Rationale**: Transparency and small diffs keep AI-assisted work auditable and reversible.

## Quality Gates & Definition of Done

A feature MAY be merged only when all of the following are true:

1. An approved `spec.md` exists with no unresolved clarification markers (Principle I).
2. `plan.md` passes the Constitution Check, with any deviation recorded and justified in
   Complexity Tracking.
3. API contracts exist in `contracts/` and match the implemented endpoints (Principle VII).
4. Unit, API integration, and validation/error-scenario tests exist and pass, along with the full
   suite (Principle VI).
5. Lint/format and build checks pass for both applications (Principle V).
6. No secrets in the diff and no out-of-scope capability introduced (Principles VIII, IX).
7. `/speckit-analyze` reports no CRITICAL findings across spec, plan, and tasks.

## Development Workflow

- Each feature is developed on its own branch created by `/speckit-specify` and stored under
  `specs/<NNN-feature-name>/`.
- `plan.md` MUST include a Constitution Check that lists each principle (I–X) as pass,
  or as a justified deviation.
- `tasks.md` MUST order tests and contracts so they are defined before or alongside the
  implementation they verify, and MUST map each task to spec requirements.
- Every change is reviewed via pull request. Reviewers MUST verify compliance with this
  constitution and MUST reject changes that violate a MUST rule without a recorded, approved
  justification.

## Governance

- This constitution supersedes all other development practices, guides, and conventions for
  this repository. Where a template, guide, or agent instruction conflicts with it, this
  constitution wins.
- All specifications, plans, task lists, and implementations MUST comply. Specs MUST stay within
  the scope of Principles IV and IX; plans MUST pass the Constitution Check; implementations
  MUST pass the Quality Gates above.
- Deviations from a MUST rule require an entry in the plan's Complexity Tracking (what, why,
  simpler alternative rejected) and explicit reviewer approval. Repeated deviations of the same
  kind MUST trigger an amendment proposal.
- **Amendments**: proposed via `/speckit-constitution` in a pull request that states the
  change, rationale, and impact on in-flight features. Amendments require approval from the
  project's technical lead/owner before merge.
- **Versioning** (semantic): MAJOR for removing or redefining a principle or lifting/reversing a
  constraint in an incompatible way; MINOR for adding a principle/section or materially expanding
  guidance (e.g. lifting a Principle IX restriction such as allowing persistence); PATCH for
  clarifications and wording fixes.
- **Compliance review**: compliance is checked at every plan (Constitution Check), every pull
  request review, and every `/speckit-analyze` run. The constitution itself SHOULD be reviewed at
  least once per release milestone for relevance.

**Version**: 1.0.0 | **Ratified**: 2026-11-06 | **Last Amended**: 2026-11-06
