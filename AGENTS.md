# Project Agent Guide

## What This Is

This repository contains the simulation asset management system. The frontend is
React 18, TypeScript, Vite, Ant Design, and styled-components. The backend is
Java 21, Spring Boot, Spring JDBC, and OceanBase in MySQL-compatible mode.

## Read By Task

- For every business change, read only the relevant terms in `CONTEXT.md`.
- For product behavior, read the relevant section of `requirement.md` and its
  acceptance scenario. Do not read the complete document by default.
- For backend or API work, read the relevant parts of
  `docs/technical-design.md`, especially sections 2, 3, 6, 8, and 11.
- For frontend work, read the relevant feature directory and sections 4, 8,
  and 11 of `docs/technical-design.md`.
- For database work, start in `docs/migrations/`. Read `sql结构.md` only when
  the task concerns compatibility with the legacy schema.
- For architectural decisions, start with `docs/adr/README.md` and open only
  the relevant accepted ADRs. Superseded ADRs are historical context.
- For local MySQL integration, read `docs/local-development.md`.
- Do not inspect `.docx`, `node_modules`, `dist`, `target`, `.playwright-cli`,
  or `output` unless the task explicitly requires an artifact from them.

## Hard Rules

- Use `pnpm` for frontend commands. Do not create `package-lock.json`.
- Use `rtk` for noisy Git, Maven, pnpm, build, test, diff, and log output. Use
  `rtk proxy` when an unfiltered failure is needed for diagnosis.
- Locate code with `rg`, then read the smallest useful file range.
- Preserve the current default in-memory backend profile. Never connect to or
  mutate a production database during development or verification.
- Do not change legacy primary keys or overwrite legacy source values.
- Product and production filters must match within the same `AssetScope`; do
  not combine matches from different scopes.
- The asset lifecycle is `草稿 -> 待整理 -> 已标准化 -> 已停用`. ADR-0017 and
  the current `requirement.md` take precedence over earlier lifecycle designs.
- Keep controllers, application services, domain types, and infrastructure
  adapters within the dependency direction documented in the technical design.
- Do not commit credentials, local environment files, uploaded data, generated
  browser artifacts, or build output.

## Verification

Run the smallest relevant check first. Broaden verification for shared
contracts, cross-module changes, or release-ready work.

```bash
# Frontend
cd frontend
rtk pnpm lint
rtk pnpm typecheck
rtk pnpm build

# One backend test class
cd backend
rtk mvn -Dtest=AssetControllerTest test

# Full backend suite
cd backend
rtk mvn test
```

- Frontend-only changes: run lint and typecheck; run build for routing, bundling,
  configuration, or cross-feature changes.
- Backend-only changes: run the directly affected test class first; run the
  full suite for shared API, repository, configuration, or domain changes.
- UI behavior without automated coverage: provide browser evidence for the
  affected workflow at an appropriate desktop viewport.
- Do not repeat an unchanged successful check. Diagnose repeated failures before
  rerunning the same command.

## Before Finishing

- Review only the relevant diff and confirm unrelated user changes remain intact.
- Report the files changed, checks run, and any behavior that still needs human
  confirmation.
- Keep the final report concise; do not paste full files, logs, or test output.
