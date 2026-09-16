# Design — accept-payment

Follows the org design template (`.kiro/steering/org/design-template.md`).

## 1. Overview

Implements the accept-payment feature: validate a payment request, authorize it,
persist it, and emit a lifecycle event. Satisfies `requirements.md` REQ-1–REQ-5.

## 2. Goals & Non-Goals

- **Goals:** authorize a payment via API; enforce auth and PCI masking; idempotent
  submission; emit terminal-state events.
- **Non-Goals:** settlement/reconciliation, refunds, and UI. Capture beyond
  authorization is out of scope for this spec.

## 3. Requirements Traceability

| Requirement | Addressed by |
|---|---|
| REQ-1 | §4 API, §6 flow |
| REQ-2 | §4 API (authorizer), §7 security |
| REQ-3 | §7 security (masking) |
| REQ-4 | §6 flow (idempotency store) |
| REQ-5 | §6 flow (event emission) |

## 4. Architecture

API Gateway (HTTP API + JWT authorizer) → Lambda (Node.js 20 / TS5) → DynamoDB
(payments + idempotency tables) → EventBridge (lifecycle events). CDK v2 defines
all infrastructure.

## 5. Data Model

- `payments` table: PK `paymentId`; attributes: status, amount, currency,
  createdAt, maskedPan. PII/cardholder fields masked at rest and on read.
- `idempotency` table: PK `idempotencyKey` → `paymentId`, with TTL.

## 6. API / Interface

- `POST /payments` — body: amount, currency, card token, idempotencyKey.
  Responses: `200` {paymentId, status}, `400`, `401`, `403`, `409` (idempotency
  conflict). Requires scope `payments:write`.

## 7. Security & Compliance

- JWT verified at API Gateway; scope enforced in the handler.
- No PAN/cardholder data in logs; mask before any write (org `security.md`, PCI).
- Least-privilege IAM: handler limited to its two tables and the event bus.

## 8. Failure Modes & Resilience

- Downstream authorizer timeout → a retriable error is surfaced to the caller;
  idempotency prevents duplicate charges on retry.

## 9. Performance

- p95 target for `POST /payments` documented and monitored; no >10% regression.

## 10. Testing Strategy

- Unit tests for validation, masking, idempotency; integration test against
  DynamoDB Local. 80% branch coverage floor.

## 11. Rollout & Rollback

- Deploy via CDK; feature flag the route. Rollback = redeploy previous version;
  no destructive data migration.

## 12. Alternatives Considered

- SNS/SQS for events — rejected in favor of the org-standard EventBridge.

## 13. Open Questions

- Which authorizer/processor integration is the initial target? (owner: service team)
