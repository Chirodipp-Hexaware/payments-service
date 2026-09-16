---
inclusion: always
---

# Repository & Module Structure — payments-service

## Layout

```
payments-service/
├── CODEOWNERS
├── src/                     # application code (Node.js 20 / TypeScript 5)
└── .kiro/
    ├── steering/
    │   ├── org/             # synced from kiro-guardrails-dist — do not edit
    │   ├── product.md       # project-owned
    │   ├── tech.md          # project-owned
    │   └── structure.md     # project-owned (this file)
    ├── specs/<spec-name>/   # requirements.md, design.md, tasks.md, .history/, artifacts/
    ├── hooks/
    │   ├── org/             # synced org hooks — do not edit
    │   └── *.hook.json      # project-specific hooks
    └── settings/mcp.json    # project MCP servers (bounded by org allow-list)
```

## Conventions

- Source lives under `src/`. Keep modules small and single-purpose per the org
  `coding-standards.md`.
- Cross-service calls go through the approved API / event-bus layer only — no
  direct data-store access across service boundaries.
- New cross-service eventing uses Amazon EventBridge.

## Steering Precedence

1. Org synced steering in `.kiro/steering/org/` is the baseline (do not edit here).
2. Project steering (`product.md`, `tech.md`, `structure.md`) refines it.
3. Where they conflict, the stricter org rule wins.

## Sync Boundary

- `.kiro/steering/org/` and `.kiro/hooks/org/` are owned by the platform team and
  overwritten by the `kiro-sync` workflow. Project-specific changes go in the
  non-`org/` files/folders.

## Naming

- Service/folder names: kebab-case. Spec folders: kebab-case under `.kiro/specs/`.
- Feature branches: `feature/<spec-folder-name>`; hotfix branches: `hotfix/<defect-id>`.
