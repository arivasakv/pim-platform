# Source of Truth

This directory holds the **authoritative requirements** for pim-platform (tier 1 under
constitution Principle XI). For their subject, documents here take precedence over specs,
plans, technical documentation, and code.

## What belongs here

| Folder | Contents |
|---|---|
| `business/` | Business rules, policies, domain definitions, glossary |
| `functional-requirements/` | Approved functional requirements and user stories |
| `technical/` | Mandated technical constraints (not design decisions) |
| `architecture/` | Mandated architectural constraints from the business or the enterprise |
| `integrations/` | Agreed behavior of external systems and interfaces |
| `reference-data/` | Controlled values, code lists, master data samples |

These do **not** belong here: generated specs (`specs/`), plans, ADRs (`docs/decisions/`),
design documentation (`docs/architecture/`), and analysis notes. They are derived artifacts.

## How a document becomes authoritative

1. Its owner, or the user, explicitly approves it as a requirement.
2. It is added here, in the matching folder, with a header that records its origin, version or
   date, and approver.
3. It is committed through a reviewed pull request.

After that, a file here is changed, renamed, moved, or deleted only when the user explicitly
asks. When a document changes, the specs, plans, contracts, tasks, and code that cite it must be
identified and reported before further work on them.

## Relation to `p360-discovery-rag`

The reference repository is a **read-only** input and is never modified.

- Its requirement documents (ERF, user stories, functional analyses) are **tier 2**. They can be
  cited as explicit requirements: `[Source: p360:<path> §<section>]`.
- Its code, tests, configuration, and database analysis are **tier 3**. They are evidence of
  observed behavior, not requirements: `[Evidence: p360:<path>:<lines>]`.
- To make a p360 document tier 1, curate it into this directory per the steps above, and keep
  its original path in the header.
- When this directory and p360 disagree, or p360's documents and code disagree, report the
  conflict and ask for clarification. Never pick one silently.

Citations for documents here: `[Source: <folder>/<file> §<section>]`.
