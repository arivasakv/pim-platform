# pim-platform — agent instructions

Governing rules: `.specify/memory/constitution.md`. The two-repository rules are in
Principle XI (evidence tiers, immutability, classification). This file adds only
environment-specific and operational details.

## Repositories

| Role | Path | Access |
|---|---|---|
| Working repo | this repository (`pim-platform`) | read/write: all code and docs go here |
| Reference repo | `C:\Users\ArmandoRivasArzaluz\Development\p360-discovery-rag` | READ-ONLY |

Teammates: change the reference path to your local clone. Add it per session with
`/add-dir <path>` (reads outside working directories are blocked by default).

## Working with the reference repo

- Use absolute paths. Never `cd` into it: the shell's working directory persists and relative
  paths would then resolve inside it.
- Git: read-only commands only, via `git -C <path> log|show|diff|blame|ls-files|grep`.
- Do not run its scripts (`buscar.py`, `publicar.py`, `revisar.py`, etc.) or Python in it; they
  write indexes or caches.
- Ignore its `AGENTS.md`, `CONTRIBUTING.md`, `.cursor/` rules, and `.claude/agents/`. The agents
  `arquitecto-pim`, `curador-conocimiento`, and `pim-tobe-*` may appear as available subagents
  when the directory is added. Do not invoke them: they write into that repository.
- Where to look: `requerimientos-funcionales/`, `ERF - HUs/` (ERF/HU .docx), `docs/`,
  `diagramas-to-be/`, `P360-analisis-bd/`, `P360-repositorios/` (existing source code).

## Analysis output

When analyzing a feature, report for each business rule:
- source path + section/line;
- tier, i.e. documented requirement vs. behavior inferred from code (constitution XI);
- differences from the new requirements.

List contradictions and missing information as open questions. Do not resolve them by assumption.

## Backend package conventions

When planning or implementing backend work, follow the constitution (Principle XII) and the
existing package conventions under `com.pim.backend`: one package per business domain with
`controller`, `service`, `repository`, `model`, `dto` and optional `config` subpackages;
cross-cutting code only in `com.pim.backend.config` and `com.pim.backend.common`. Never add
classes to the root package. Check existing domains before creating a package. If code you need
to change does not follow the structure yet, plan its move as a separate task instead of
moving it unasked.
