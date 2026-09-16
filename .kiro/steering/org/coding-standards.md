---
inclusion: always
---

# Coding Standards

Org-wide coding conventions. Always loaded into Kiro context. Enforced in review
by `coding_standards_agent` and, where automatable, by hooks in `hooks/`.

## General Principles

- Optimize for readability first; clever code that is hard to follow is a defect.
- Keep functions small and single-purpose. Prefer early returns over deep nesting.
- No dead code, commented-out blocks, or `TODO` without a linked ticket.
- Every change traces back to a requirement (spec workflow) or a ticket.

## Naming

- Use descriptive, intention-revealing names. Avoid abbreviations except widely
  understood ones (`id`, `url`, `db`).
- Files and folders: `kebab-case`. Types/classes: `PascalCase`.
  Functions/variables: `camelCase` (JS/TS) or language idiom.
- Constants: `UPPER_SNAKE_CASE`.

## Language Baselines

- **TypeScript/Node.js**: strict mode on (`"strict": true`). No implicit `any`.
  Prefer `const`; use `async/await` over raw promise chains.
- **Java**: target the LTS agreed for the service. Prefer immutability; validate
  inputs at the boundary; never log secrets or PII.

## Error Handling

- Fail fast at boundaries; validate all external input before use.
- Never swallow exceptions silently. Log with context, then handle or rethrow.
- Return typed, structured errors to callers — do not leak internal detail or
  stack traces to clients.

## Documentation

- Public APIs, exported functions, and non-obvious logic carry doc comments.
- Keep comments about *why*, not *what*; the code already says what.

## Formatting

- Formatting is enforced by the project formatter (Prettier / Spotless / gofmt).
  Do not hand-format; run the formatter. The `lint-on-save` hook runs it on save.

## Dependencies

- Add dependencies deliberately. Pin versions. Prefer well-maintained, widely
  adopted libraries over niche ones. Flag anything that looks like a
  typosquat before adding it.
