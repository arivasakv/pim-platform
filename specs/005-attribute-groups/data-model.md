# Data Model: Attribute Management – Attribute Groups

**Feature**: `005-attribute-groups` | **Date**: 2026-11-09 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

Stored in MongoDB, collection `attribute_groups` (research R-02, ADR 0003): `{ _id: <UUID>, name,
nameKey, description, attributeIds, createdAt }`, unique index on `nameKey` (the name lower-cased).
Model types live in `com.pim.backend.attributegroup.model`; the Spring Data document stays in
`attributegroup.repository`.

## AttributeGroup (entity)

| Field | Type | Rule |
|---|---|---|
| `id` | text (UUID) | Generated at creation; never changes (FR-016, R-04) |
| `name` | GroupName | Required; trimmed; 1–80 characters; unique among groups ignoring letter case (FR-003, R-05) |
| `description` | text, optional | Trimmed; up to 500 characters; blank = absent (R-05) |
| `attributeIds` | ordered set of attribute system IDs | Each an existing feature 004 attribute; at most once per group, compared ignoring case (FR-007, FR-008) |
| `createdAt` | timestamp (UTC) | Set once |

- Flat: no parent or child groups (FR-004).
- No groups exist at start (FR-015).
- Renaming or editing the description keeps `id` and `attributeIds` (FR-005).
- Deletable only when `attributeIds` is empty (FR-005).

## GroupName (value object)

Trimmed text, 1–80 characters. Equality and the uniqueness check ignore letter case (`Locale.ROOT`);
the value keeps the case given. Invalid → 400 `INVALID_GROUP_NAME` (blank or too long) or `REQUIRED`
(absent).

## Membership

Not a separate entity: the attribute ID inside a group's `attributeIds`. Adding or removing it never
touches the attribute (FR-010). An attribute may be in any number of groups, including none (FR-007).

## Attribute (feature 004, read only)

Read through `AttributeService.find` / `findAll` (constitution III): system ID, display name, data type.
Shown in a group's member list.

## Error codes

400 `REQUIRED`, `INVALID_GROUP_NAME`, `INVALID_GROUP_DESCRIPTION`, `INVALID_ATTRIBUTE_IDS` (empty list
or more than 100); 403 `ATTRIBUTE_CREATE_NOT_PERMITTED` (as in feature 004); 404 `GROUP_NOT_FOUND`,
`ATTRIBUTE_NOT_IN_GROUP`; 409 `GROUP_NAME_IN_USE`, `GROUP_NOT_EMPTY`; 422 `ATTRIBUTE_NOT_FOUND`.

## Operation flows

```text
create / rename:  permission → name and description format → name unique (other groups) → save
delete:           permission → group exists → empty? → remove                     (409 GROUP_NOT_EMPTY)
add attributes:   permission → group exists → every ID exists (feature 004)       (422, nothing added)
                  → add the new ones; report the ones already in the group
remove attribute: permission → group exists → is a member?                        (404 ATTRIBUTE_NOT_IN_GROUP)
```

All changes run under one lock (R-12).
