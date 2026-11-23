# pim-platform

> **Proof of concept.** This repository is a POC for a new Product Information Management (PIM)
> platform for Liverpool and Suburbia. It is not production software: there is no authentication,
> no real external integration, and data is stored in a local MongoDB with sample data. Scope and
> design can change at any point.

## What it is

The POC explores how a new PIM could replace the current P360-based tooling. It is built one
feature at a time with [Spec Kit](https://github.com/github/spec-kit): every feature has an
approved specification, plan and task list under [`specs/`](specs/) before any code is written.

Features built so far:

| # | Feature |
|---|---|
| 001 | Liverpool-inspired look and feel for the frontend |
| 002 | Search for an existing product (by EAN, per business) |
| 003 | System identifier management (`ATR000001`, …) |
| 004 | Attribute creation |
| 005 | Attribute groups |
| 006 | Lists of values (LOV), including CSV import |
| 007 | Consult, edit and delete attributes |
| 008 | Live preview of an attribute's input field |
| 009 | Organizing attributes by attribute group |
| 010 | Attribute inheritance through the product taxonomy |
| 011 | Move taxonomy nodes with their branch (inheritance follows the new location) |
| 012 | List-value filters per taxonomy level (restrict list values on a node; inherited and only narrowed below) |
| 013 | Bulk load of lists of values («Carga Masiva de LOV»): several lists from one Excel or CSV file, checked first, then confirmed |

### Deliberate limits of the POC

Defined in the project constitution ([`.specify/memory/constitution.md`](.specify/memory/constitution.md),
Principle IX):

- MongoDB is used only for the product catalog and for attribute administration data
  (attributes, groups, identifiers, lists of values, taxonomy and its list-value filters). Everything
  else is in-memory mock data.
- No SQL databases, no integrations with commerce platforms, ERPs, DAMs or messaging, and no
  AI-powered features.
- No login. User permissions are simulated with frontend environment variables (see below).

## Architecture

Two separate applications that communicate only through a REST (JSON) API:

| App | Folder | Stack |
|---|---|---|
| Backend | [`backend/`](backend/) | Java 21, Spring Boot 4.1, Gradle 9 (wrapper included), Spring Data MongoDB |
| Frontend | [`frontend/`](frontend/) | React 19, TypeScript (strict), Vite, Vitest, ESLint, Prettier |
| Database | — | MongoDB, running locally |

The backend is a modular monolith with one package per business domain under `com.pim.backend`
(`product`, `attribute`, `attributegroup`, `identifier`, `lov`, `taxonomy`), each split into
`controller`, `service`, `repository`, `model` and `dto`. API errors use RFC 9457 Problem Details.

## Repository layout

```
backend/                  Spring Boot API
frontend/                 React + Vite web app
specs/NNN-feature/        Spec Kit artifacts per feature (spec, plan, tasks, contracts)
docs/decisions/           Architecture decision records (ADRs)
docs/architecture/        Design documentation (design system, …)
docs/source-of-truth/     Authoritative requirements (tier 1)
.specify/                 Spec Kit configuration and the project constitution
```

## Local setup

### 1. Prerequisites

| Tool | Version | Check |
|---|---|---|
| Git | any recent | `git --version` |
| JDK | 21 | `java -version` |
| Node.js | 22 LTS or later (includes npm) | `node --version` |
| MongoDB Community Server | any current version, listening on `localhost:27017` | `mongosh --eval "db.version()"` |

You do not need to install Gradle: the backend uses the Gradle wrapper (`gradlew` / `gradlew.bat`),
which downloads the right version on first run. Docker is not required.

MongoDB must run without credentials on the default port, or you must point the backend at another
instance (see [Configuration](#configuration)). On Windows, the MongoDB installer can register it as
a service that starts automatically.

### 2. Clone

```bash
git clone <repository-url>
cd pim-platform
```

### 3. Start the backend

```powershell
cd backend
.\gradlew.bat bootRun        # Windows
./gradlew bootRun            # macOS / Linux
```

The API runs at **http://localhost:8080**. On first start it creates the MongoDB indexes and loads a
sample catalog of 531 products into the `pim` database. Restarts never insert duplicates.

If MongoDB is not reachable, the backend does not start. If MongoDB stops later, the API answers
`503`.

### 4. Start the frontend

In a second terminal:

```powershell
cd frontend
Copy-Item .env.example .env  # Windows (PowerShell)
cp .env.example .env         # macOS / Linux
npm install
npm run dev
```

Open **http://localhost:5173**.

To try the attribute administration screens that need permissions, uncomment these lines in
`frontend/.env` and restart `npm run dev`:

```
VITE_DEV_PERMISSIONS=ATTRIBUTE_CREATE
VITE_DEV_MANUAL_ID_PERMISSIONS=ATTRIBUTE
```

These are development stand-ins until authentication exists. They are not secrets.

## Configuration

Every setting has a local default. Override it with an environment variable; never commit
credentials.

| Variable | App | Default | Purpose |
|---|---|---|---|
| `PIM_MONGODB_URI` | backend | `mongodb://localhost:27017/pim?serverSelectionTimeoutMS=2000` | MongoDB connection for the app |
| `PIM_TEST_MONGODB_URI` | backend | `mongodb://localhost:27017/pim-test?serverSelectionTimeoutMS=2000` | MongoDB connection for tests |
| `PIM_CORS_ALLOWED_ORIGINS` | backend | `http://localhost:5173` | Browser origins allowed to call `/api/**` (comma-separated) |
| `PIM_CATALOG_SEED_SAMPLE_DATA` | backend | `true` | Load the sample catalog when the products collection is empty |
| `VITE_API_BASE_URL` | frontend | `http://localhost:8080` | Backend URL, no trailing slash |

## Build, test and format

Backend (from `backend/`). Tests need a running local MongoDB and use their own database,
`pim-test`, which they empty and reload; they never touch `pim`.

```powershell
.\gradlew.bat build           # compile, tests, formatting check (warnings fail the build)
.\gradlew.bat test            # tests only
.\gradlew.bat spotlessApply   # format Java code
```

Frontend (from `frontend/`):

```powershell
npm test                 # Vitest
npm run lint             # ESLint
npm run format           # Prettier (write)
npm run format:check     # Prettier (check)
npm run build            # type-check and production build
```

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Backend fails at startup with a MongoDB timeout | MongoDB is not running on `localhost:27017`, or `PIM_MONGODB_URI` is wrong |
| Frontend shows a connection error with "Reintentar" | Backend not running, or `VITE_API_BASE_URL` points to the wrong address |
| Browser console shows a CORS error | The frontend runs on a different origin; add it to `PIM_CORS_ALLOWED_ORIGINS` |
| Backend build fails on tests | MongoDB is not running (tests need it) |
| Changes to `.env` have no effect | Restart `npm run dev`; Vite reads `.env` only at startup |

## How work is done here

- Governing rules: [`.specify/memory/constitution.md`](.specify/memory/constitution.md).
- New features follow the Spec Kit flow: specify → clarify → plan → tasks → implement → analyze.
- One branch per feature (`NNN-short-name`), merged into `main` when done.
- Key decisions are recorded as ADRs in [`docs/decisions/`](docs/decisions/).
- Requirements come from `docs/source-of-truth/` and from the read-only reference repository
  `p360-discovery-rag` (analysis of the existing P360 system). See
  [`docs/source-of-truth/README.md`](docs/source-of-truth/README.md).
