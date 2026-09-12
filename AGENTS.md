# Project Agent Guide

## What This Is

This repository contains the simulation asset management system. The frontend is
React 18, TypeScript, Vite, Ant Design, and styled-components. The backend is
Java 21, Spring Boot, Spring JDBC, and OceanBase in MySQL-compatible mode.

## How Work Is Driven

Development intent starts from the conversation, not from maintained
requirement/design documents (those lines are retired; see Document Locations).
Work flows through the Matt Pocock skill set installed at `~/.dsh/skills`
(MIT; attribution in `LICENSE.mattpocock` there):

- `ask-matt` routes a situation to the fitting skill.
- Sharpen the intent first with `grilling` / `grill-with-docs` / `wait-what`.
- `to-spec` synthesizes a feature spec; `to-tickets` breaks it into vertical
  tracer tickets, each with acceptance criteria and blocking edges. Each ticket
  carries a `测试：` line listing its cases as normal / boundary / error.
- Implement test-first with `tdd` + `implement`; land each ticket as a small,
  independently verifiable commit.
- Finish with `code-review`; use `diagnosing-bugs` for hard regressions,
  `research` for source-backed answers, and `resolving-merge-conflicts` for
  in-progress git conflicts.

A new requirement runs through one sequence. `docs/development-flow.md` holds the
full table (owner, artifact, exit criteria, per-phase commands) plus the
shortcuts for hotfixes, bug diagnosis, research, and conflicts:

1. Route and sharpen the intent (`ask-matt` / `grilling` / `wait-what`).
2. `to-spec` publishes the spec into `docs/plans/<yyyy-mm-dd>-<slug>.md`.
3. `to-tickets` cuts vertical tracer tickets with blocking edges.
4. Agree the seams under test and list each case as normal / boundary / error.
5. Implement test-first, one case at a time (`tdd` + `implement`).
6. Run the smallest relevant verification (see Verification below).
7. `code-review` both axes; refactor there, not inside the red-green loop.
8. Commit per ticket (backend and frontend separately), then archive the spec.

Artifact discipline (kept as governance): no issue tracker is configured, so
`to-spec` / `to-tickets` publish into
`docs/plans/<yyyy-mm-dd>-<slug>.md` (one file per feature, tickets embedded or
split beside it). Never create `.scratch/`, ticket dumps, or one-off progress
notes at the repository root. Research notes go under `docs/research/`.

## Read By Task

- Domain terms or legacy business rules: consult the archived reference only
  when a task touches legacy behavior —
  `docs/archive/2026-09-07-doc-driven-development/root/CONTEXT.md` for terms,
  and the ADRs under `docs/archive/2026-09-07-doc-driven-development/adr/` for
  historical decisions.
- Legacy-schema compatibility: archived
  `docs/archive/2026-09-07-doc-driven-development/migrations/` plus the
  backend's own resources.
- Local MySQL integration: `docs/local-development.md`.
- Everything else: the code is the fact — read the relevant `backend/` package
  or `frontend/` feature directory and neighboring implementations, and follow
  the existing layering (controller → application service → domain →
  infrastructure adapter) and naming conventions you observe there.
- Do not inspect `.docx`, `node_modules`, `dist`, `target`, `.playwright-cli`,
  or `output` unless the task explicitly requires an artifact from them.

## Repository Structure

Keep the repository root clean and stable. The root is a whitelist; everything
else lives in a named directory.

Allowed at root: `AGENTS.md`, `README.md`, `.agents/`, `.claude/`, `backend/`,
`docs/`, `frontend/`, `scripts/`, `skills-lock.json`, and standard dotfiles
(`.editorconfig`, `.env.example`, `.env.local`, `.gitignore`).

- Never create cache or generated-artifact directories at the root
  (`.pnpm-store`, `.playwright-cli`, `.superpowers`, `.worktrees`, `output/`,
  `node_modules/`, `dist/`, `target/`). Project-local agent skills belong under
  `.agents/skills/` and are pinned by `skills-lock.json`; other generated
  artifacts belong under `/tmp` or `scripts/e2e/.logs/`.
- Run `scripts/check_repo_structure.sh` before committing; it fails on any
  unexpected root entry.

### Document Locations

Active documentation is kept minimal:

| What | Where |
| --- | --- |
| Local development / DB runbook | `docs/local-development.md` |
| New-requirement development flow | `docs/development-flow.md` |
| Testing strategy and verification layers | `docs/testing-strategy.md` |
| Feature specs and tickets (active) | `docs/plans/` |
| Research findings (active) | `docs/research/` |
| Retired doc-driven regime (frozen history) | `docs/archive/2026-09-07-doc-driven-development/` |

The retired tree holds the former requirement.md baseline and generated docx,
CONTEXT.md glossary, module requirements, technical design, ADRs, migrations,
design specs, R2C pipeline/template assets (`.ai/`, `.prompt/`), and the docx
generator script. Treat it as read-only history; never revive a "source of
truth" document line from it without an explicit human decision.

New documentation goes under `docs/` only. Never add new markdown, docx, or
PDF files at the repository root.

## Hard Rules

- Use `pnpm` for frontend commands. Do not create `package-lock.json`.
- Use `rtk` for noisy Git, Maven, pnpm, build, test, diff, and log output. Use
  `rtk proxy` when an unfiltered failure is needed for diagnosis.
- Locate code with `rg`, then read the smallest useful file range.
- The real database is the only data source. The backend defaults to the
  `local` profile and talks to the configured MySQL/OceanBase instance; there
  are no in-memory repositories or seeded mock data in `src/main`. In-memory
  implementations live in `src/test` as test doubles only. Never connect to or
  mutate a production database during development or verification.
- Do not change legacy primary keys or overwrite legacy source values.
- Product and production filters must match within the same `AssetScope`; do
  not combine matches from different scopes.
- The asset lifecycle is `草稿 -> 待整理 -> 已标准化 -> 已停用`.
- Do not commit credentials, local environment files, uploaded data, generated
  browser artifacts, or build output.
- Keep the repository root whitelisted (see Repository Structure); run
  `scripts/check_repo_structure.sh` before committing and fix every violation.
- API error codes are declared only in
  `backend/src/main/java/com/tianshu/assets/common/api/ErrorCode.java`; never
  write a response code as a string literal. Uniqueness of `code()` is asserted
  by `ErrorCodeTest`, so a duplicate fails `mvn test`.
- The API error body has exactly one shape (`ApiError`) and the frontend has
  exactly one mirror (`frontend/src/types/api.ts` → `ApiErrorBody`). Do not
  redeclare it in a service or feature.
- Schema changes under `scripts/db/migrations/` require a spec or a
  `docs/plans/` record first. Destructive changes (dropping a column, changing a
  column type, adding NOT NULL) additionally stop for explicit human
  confirmation.
- Adding a dependency to `pom.xml`, `package.json`, or the lockfiles requires a
  stated reason in the spec or the commit body. Do not add a dependency to avoid
  writing a small amount of code.
- Every outbound call and external process declares a bounded timeout and a
  defined failure path — follow `HttpAiCapabilityClient` (connect/request
  timeouts, mapped errors) and `LibreOfficeDocumentPreviewConverter` (`waitFor`
  timeout, then `destroyForcibly`). State the retry policy (fail-fast counts) and
  give mutating calls an idempotency key as `GovernanceExecutionService` does.
- Never log or return secrets, tokens, credentials, file contents, or full
  request bodies; log with SLF4J carrying the operation and the actor. No
  request/correlation id exists yet — add one centrally, never per module.
- No repo-wide or multi-module refactor without explicit human confirmation:
  publish the change list (files, renames, deletions, API changes) and get a yes
  before writing it. Refactors stay out of the red → green loop and out of
  feature tickets.
- Test cases for business rules (state transitions, statistics, `AssetScope`
  filters, numbering and settlement rules) are confirmed by a human before
  implementation, and model-authored cases are drafts. An expectation that
  recomputes the implementation's formula is not a test.

## Git Commits

- Every completed, independently verifiable version must be committed after its
  required checks pass; do not leave a completed version only in the worktree.
- Keep backend and frontend changes in separate commits, even when they belong
  to the same product version. Contract or documentation changes use their own
  commit when they are independently reviewable.
- Use Chinese Conventional Commit messages, for example
  `feat(后端): 实现文档首次发布` and `feat(前端): 实现文档检索工作台`.
- Stage only files belonging to the current version and layer. Never include
  unrelated user changes or generated artifacts in a version commit.
- Do not commit before verification. Record the product version in the commit
  body when one version contains multiple frontend/backend commits.

## Verification

Run the smallest relevant check first. Broaden verification for shared
contracts, cross-module changes, or release-ready work.

```bash
# Repository structure hygiene (run first; must pass)
scripts/check_repo_structure.sh

# Frontend
cd frontend
rtk pnpm lint
rtk pnpm typecheck
rtk pnpm build   # release gate only: routing/lazy-load/Vite config/cross-feature/release

# One backend test class
cd backend
rtk mvn -Dtest=AssetControllerTest test

# Full backend suite
cd backend
rtk mvn test
```

- Frontend-only changes: the daily commit gate is lint + typecheck. Run
  `pnpm build` only for whitelisted triggers (release gate): routing or
  lazy-loaded entry points, Vite/bundler configuration, cross-feature page
  mounting, or a release/acceptance checkpoint. When build is required, wrap it
  in `rtk` and judge by exit code and failure summary, not full logs.
- Backend-only changes: run the directly affected test class first; run the
  full suite for shared API, repository, configuration, or domain changes.
- UI behavior without automated coverage: provide browser evidence for the
  affected workflow at an appropriate desktop viewport.
- Do not repeat an unchanged successful check. Diagnose repeated failures before
  rerunning the same command.
- Local commit gate: `bash scripts/install-hooks.sh` installs the versioned hook
  from `scripts/git-hooks/` (structure whitelist + secret scan + frontend
  lint/typecheck when `frontend/` is staged). Backend tests stay out of the hook
  on purpose; run them yourself. Bypass only with `--no-verify`, and say why in
  the commit body.

## Before Finishing

- Review only the relevant diff and confirm unrelated user changes remain intact.
- Report the files changed, checks run, and any behavior that still needs human
  confirmation.
- Keep the final report concise; do not paste full files, logs, or test output.
