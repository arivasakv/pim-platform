# ADR 0003: MongoDB for attribute administration data

- **Status**: Accepted
- **Date**: 2026-11-09
- **Approved by**: the project owner
- **Origin**: constitution 2.3.0 (Principle IX); feature 005 planning (attribute groups must be
  persisted), with storage amendments of features 003 and 004
  ([005 plan](../../specs/005-attribute-groups/plan.md) D-1,
  [003 plan](../../specs/003-identifier-management/plan.md) and
  [004 plan](../../specs/004-attribute-creation/plan.md) amendments of 2026-11-09)
- **Amended 2026-11-11** (constitution 2.4.0, feature 006): lists of values and their values, plus
  their change records, are stored with the other attribute administration data
  ([006 plan](../../specs/006-lov-management/plan.md) D-2, D-10). Rows `lists_of_values` and
  `lov_changes` were added to the collections table.

## Context

Features 003 (system identifiers), 004 (attribute creation) and 005 (attribute groups) were planned
with in-memory storage, because constitution 2.2.0 allowed MongoDB only for the product catalog. The
project owner asked that attribute groups be persisted. Groups hold attribute system IDs, so storing
only the groups would break after a restart: attributes and the identifier registry would be empty,
the ID sequence would start again at `ATR000001`, and new attributes would silently appear in old
groups. The owner chose to persist all three together and amended the constitution (2.3.0) to allow
MongoDB for "attribute administration data". Behaviour of features 003 and 004 does not change; only
where their data lives.

## Decision

1. **Store**: the same MongoDB database and connection as the product catalog (ADR 0002:
   `spring.mongodb.uri`, `PIM_MONGODB_URI`, default local `pim`, no credentials in source). No new
   dependency: `spring-boot-starter-data-mongodb` is already present.
2. **Collections** (one owner domain each, constitution III):

   | Collection | Owner | Document | Indexes |
   |---|---|---|---|
   | `system_ids` | `identifier` (003) | `{ _id: <ID upper-cased>, systemId, objectType, keyFieldValues, assignment, registeredAt }` | `_id` (global uniqueness ignoring case, FR-001) |
   | `object_type_configurations` | `identifier` (003) | `{ _id: <objectType>, sequence: { kind, prefix, minimumDigits, nextValue }, keyFields }` | `_id` |
   | `attributes` | `attribute` (004) | `{ _id: <system ID upper-cased>, systemId, displayName, displayNameKey, description, dataType, scope, properties, createdAt }` | `_id`; unique `attribute_display_name_unique` on `displayNameKey` (FR-023) |
   | `attribute_groups` | `attributegroup` (005) | `{ _id: <UUID>, name, nameKey, description, attributeIds, createdAt }` | `_id`; unique `attribute_group_name_unique` on `nameKey` (FR-003) |
   | `lists_of_values` | `lov` (006) | `{ _id: <list system ID upper-cased>, systemId, name, nameKey, equivalencesEnabled, nextGeneratedNumber, values: [ { id, idKey, label, equivalence } ], createdAt, updatedAt }`; values embedded so each change is one atomic document update (006 research R-02; at most 50,000 values) | `_id`; unique `lov_name_unique` on `nameKey` (006 FR-003) |
   | `lov_changes` | `lov` (006) | `{ _id: <UUID>, at, user, operation, listId, listName, valueIds, added, updated }` (006 FR-019, research R-11) | `_id`; `{ listId: 1, at: -1 }` |

   `*Key` fields hold the value lower-cased with `Locale.ROOT`, so uniqueness ignoring letter case is
   also enforced by the database.
3. **Layers** (constitution XII): the existing repository interfaces stay
   (`SystemIdRegistry`, `ObjectTypeConfigurationRepository`, `AttributeRepository`, and
   `AttributeGroupRepository`); MongoDB implementations replace the in-memory ones in production code.
   Spring Data documents stay package-private in each domain's `repository` package and are mapped to
   model types. Services and their rules do not change. In-memory implementations may remain as test
   fakes under `src/test`.
4. **Startup**: each domain creates its indexes. Feature 003 inserts a default object-type
   configuration **only for types that have none**, so a changed configuration or an advanced sequence
   survives restarts. No sample attributes or groups are loaded.
5. **Consistency**: the in-process locks of the services stay (one backend instance). Unique indexes
   back the uniqueness rules. A standalone MongoDB has no multi-document transactions: if saving an
   attribute fails after feature 003 registered its ID, the ID stays registered and unused (the same
   outcome as before, and never reused). Groups store attribute IDs; attributes are never deleted in
   these features, so memberships cannot dangle.
6. **Unavailable store**: as in ADR 0002, `DataAccessResourceFailureException` answers 503 through
   `common.DataAccessExceptionHandler`; nothing falls back to memory.
7. **Tests**: integration tests use the local `pim-test` database (as feature 002) and empty the
   collections they use before each test, because data now outlives a Spring context. Tests of
   features 003 and 004 are updated for this (amendments of 2026-11-09).

## Alternatives considered

- **Persist groups only** — rejected: IDs restart after a restart and attach new attributes to old
  groups.
- **Persist groups and attributes, keep IDs in memory** — rejected: the sequence restarts and new
  attributes collide with stored ones.
- **Keep everything in memory** — rejected by the owner: groups must be persisted.
- **Separate database per domain** — rejected: no need yet; collections already separate ownership.

## Consequences

- Attributes, groups and system IDs survive restarts; feature 003 to 005 behaviour is unchanged.
- The build and the running backend need MongoDB for these features too (already true for feature
  002).
- Features 003 and 004 change their repository implementations and some tests (their specs and plans
  record the amendment).
