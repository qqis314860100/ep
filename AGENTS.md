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
  tracer tickets, each with acceptance criteria and blocking edges.
- Implement test-first with `tdd` + `implement`; land each ticket as a small,
  independently verifiable commit.
- Finish with `code-review`; use `diagnosing-bugs` for hard regressions,
  `research` for source-backed answers, and `resolving-merge-conflicts` for
  in-progress git conflicts.

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

Allowed at root: `AGENTS.md`, `README.md`, `.claude/`, `backend/`, `docs/`,
`frontend/`, `scripts/`, and standard dotfiles (`.editorconfig`, `.env.example`,
`.env.local`, `.gitignore`).

- Never create cache or tool directories at the root (`.pnpm-store`,
  `.playwright-cli`, `.superpowers`, `.worktrees`, `output/`, `node_modules`,
  `dist/`, `target/`). Generated artifacts belong under `/tmp` or
  `scripts/e2e/.logs/`.
- Run `scripts/check_repo_structure.sh` before committing; it fails on any
  unexpected root entry.

### Document Locations

Active documentation is kept minimal:

| What | Where |
| --- | --- |
| Local development / DB runbook | `docs/local-development.md` |
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

## Before Finishing

- Review only the relevant diff and confirm unrelated user changes remain intact.
- Report the files changed, checks run, and any behavior that still needs human
  confirmation.
- Keep the final report concise; do not paste full files, logs, or test output.
