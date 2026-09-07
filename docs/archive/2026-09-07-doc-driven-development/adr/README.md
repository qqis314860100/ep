# Architecture Decision Index

Read this index before opening individual ADRs. Current product behavior follows
the accepted decisions below. When accepted decisions overlap, the newer and
more specific decision applies; ADR-0017 is authoritative for the first-phase
asset lifecycle and deliberately replaces several earlier controls.

## Current Decisions

| ADR | Decision | Read for |
| --- | --- | --- |
| [ADR-0001](0001-separate-product-and-production-hierarchies.md) | Separate product and production hierarchies | Scope modeling and search filters |
| [ADR-0004](0004-scope-data-access.md) | Restrict access by blueprint or site | Authorization and direct asset access |
| [ADR-0006](0006-retain-published-records.md) | Retain records instead of permanently deleting them | Deactivation, replacement, and audit history |
| [ADR-0014](0014-not-a-production-approval-system.md) | Do not treat the system as a production approval system | Product boundary and status semantics |
| [ADR-0015](0015-preview-is-optional.md) | Online preview is optional | File upload, preview, and download |
| [ADR-0016](0016-minimize-uploader-effort.md) | Minimize information required from uploaders | Upload forms and background governance |
| [ADR-0017](0017-submit-then-curate.md) | Submit for use, then curate in the background | Lifecycle, roles, governance, and first-phase scope |
| [ADR-0018](0018-use-blueprint-domain-term.md) | Use blueprint as the domain term while retaining legacy field names | Product scope terminology and compatibility |

## Superseded Decisions

ADRs 0002, 0003, 0005, 0007, 0008, 0009, 0010, 0011, 0012, and 0013 are
retained only to explain earlier designs. Do not implement them unless a task
explicitly asks for historical analysis or a new decision reintroduces them.

Use this command when checking status mechanically:

```bash
rg -n '^status:' docs/adr/*.md
```
