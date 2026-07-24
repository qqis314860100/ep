# Frontend

React 18 and TypeScript frontend for the simulation asset management system.
The UI uses Vite, Ant Design, styled-components, React Router, and TanStack
Query. Use `pnpm`; the repository lockfile is the dependency source of truth.

## Commands

```bash
pnpm install
pnpm dev
pnpm lint
pnpm typecheck
pnpm build
```

Run lint and typecheck for normal frontend changes. Run the production build
for routing, bundling, configuration, or cross-feature changes. Automated
component tests are not configured yet, so verify affected UI workflows in a
browser when behavior is not covered by backend tests.

Read the root `AGENTS.md` before making changes. It maps product requirements,
architecture documents, and verification scope by task type.
