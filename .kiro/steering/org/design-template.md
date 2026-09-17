# Design Template

Skeleton for `design.md`, produced by `sdlc_orchestrator_agent` during the design
phase of a spec. Fill every section; delete none. If a section does not apply,
state "N/A" and why. Audience: engineers and enterprise architects — be technical,
precise, and decision-oriented.

---

## 1. Overview

One paragraph: what this feature/service does and the problem it solves. Link the
`requirements.md` this design satisfies.

## 2. Goals & Non-Goals

- **Goals** — what this design must achieve (bullet list).
- **Non-Goals** — explicitly out of scope, to prevent scope creep.

## 3. Requirements Traceability

| Requirement ID | Addressed by (section) |
|---|---|
| REQ-1 | |

Every requirement must map to at least one design element. No orphan requirements.

## 4. Architecture

High-level component diagram and narrative. Show the request/data flow, the
services/components involved, and the boundaries between them. Note which existing
patterns are reused.

## 5. Data Model

Entities, keys, indexes, and ownership. Call out any PII fields and how they are
protected (encryption at rest, masking on read).

## 6. API / Interface

Endpoints or interfaces: method, path, request/response schema, status codes,
auth requirements (scopes, ownership checks). Reference input-validation rules.

## 7. Security & Compliance

- AuthN/AuthZ approach (token verification, scope, resource ownership).
- Secrets handling, least-privilege IAM/permissions.
- PII handling and data residency.
- Applicable guardrails and how this design satisfies them.

## 8. Failure Modes & Resilience

Error handling, timeouts, retries, idempotency, and degradation behavior.
What happens when each dependency is unavailable.

## 9. Performance

Expected load, latency budget (state the p95 target and how it is met), and any
capacity/cost considerations.

## 10. Testing Strategy

Unit / integration / contract coverage plan. Which failure branches get explicit
tests. Coverage target.

## 11. Rollout & Rollback

Deployment approach, migration steps (with reversible backup where destructive),
feature flags, and the rollback plan.

## 12. Alternatives Considered

Options weighed and why the chosen approach won. Link any ADRs.

## 13. Open Questions

Unresolved decisions and who owns each.
