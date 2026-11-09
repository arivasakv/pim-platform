# ADR 0002: MongoDB as the product catalog store

- **Status**: Accepted
- **Date**: 2026-11-09
- **Approved by**: the project owner
- **Origin**: constitution 2.2.0 (Principle IX); feature 002 amendment of 2026-11-09, plan
  decisions D-6 to D-9 ([plan.md](../../specs/002-existing-product-search/plan.md),
  [research.md](../../specs/002-existing-product-search/research.md) R-17 to R-20)

## Context

Until 2026-11-09 the backend kept feature 002's sample product catalog in memory, as constitution
Principle IX required. The project owner decided to store the catalog in MongoDB, and amended the
constitution (2.2.0) to allow MongoDB, through Spring Data MongoDB, for the product catalog only.
Principle II requires a recorded decision for the new framework. The search rules, validation and
the API's 200 and 400 responses must not change.

## Decision

1. **Store**: MongoDB, database `pim`, collection `products`. Development uses a local MongoDB
   (no Docker Compose, no hosted service). Dependency: `spring-boot-starter-data-mongodb`, with
   the version managed by Spring Boot (4.1.2: Spring Data MongoDB 5.1.2, Java driver 5.8.1).
2. **Layers** (constitution XII): `ProductRepository` stays the read-only interface the service
   uses. `MongoProductRepository` implements it through a package-private Spring Data repository
   and maps documents to the `Product` model. Spring Data types stay in `product.repository`.
   Marketplace filtering and ordering stay in `ProductSearchService`. There is no in-memory
   implementation in production code and no fallback to sample data.
3. **Document**: `{ _id, sku, ean, title, brand, business }`. `ean` holds the normalized digits as a
   string and is matched by equality only; `business` holds the enum name.
4. **Indexes**, created by the application at startup: unique `ean_business_unique`
   `{ ean: 1, business: 1 }` (the same EAN may exist in Liverpool and Suburbia, but only once per
   business; no global unique EAN) and unique `sku_unique` `{ sku: 1 }`.
5. **Sample data**: at startup the application creates the indexes and inserts the 531-product
   sample catalog only when the collection is empty (`pim.catalog.seed-sample-data`, default
   `true`, environment variable `PIM_CATALOG_SEED_SAMPLE_DATA`). Restarts insert nothing.
6. **Configuration** (constitution VIII): `spring.mongodb.uri`, default
   `mongodb://localhost:27017/pim?serverSelectionTimeoutMS=2000`, overridden with
   `PIM_MONGODB_URI`. The default has no credentials; credentials are set only in the environment.
7. **Unavailable store**: the 2-second server selection timeout makes failures fast. Spring
   translates them to `DataAccessResourceFailureException`, which `common.DataAccessExceptionHandler`
   returns as **503** Problem Details (see [ADR 0001](0001-api-conventions.md)). If MongoDB is
   unreachable at startup, the application does not start.
8. **Tests**: Spring tests use the developer's local MongoDB with database `pim-test`
   (`PIM_TEST_MONGODB_URI` overrides it), which tests empty and reload. They never use `pim`.

## Consequences

- The frontend, the API contract's 200 and 400 responses and the business rules are unchanged.
  The contract gains a 503 response (version 1.1.0); the frontend already shows its connection
  error with "Reintentar" for it.
- `.\gradlew.bat build` needs a running local MongoDB. Without it, the MongoDB-backed tests fail.
- Other domains keep in-memory data until a specification and a constitution amendment extend
  MongoDB to them (Principle IX).

## Alternatives considered

- **Testcontainers** for tests: self-contained, but needs a running Docker engine, which is not
  available in development today. Can replace decision 8 later without changing production code.
- **Embedded MongoDB** for tests: forbidden by Principle IX (embedded databases).
- **`mongoimport` with a JSON file** for sample data: an extra tool and a manual step.
- **Filtering Marketplace in the query**: puts a business rule in the repository (Principle XII).
- **Automatic index creation** (`spring.data.mongodb.auto-index-creation`): less visible than
  explicit creation and off by default in Spring Data.
