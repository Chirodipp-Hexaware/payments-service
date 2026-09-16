# Requirements — accept-payment

Written in EARS format. Every requirement is explicit and testable. Each maps to
design elements in `design.md` and implementation items in `tasks.md`.

## Introduction

The accept-payment feature lets an authenticated caller submit a payment request
for authorization. It validates the request, authorizes the payment, persists the
record, and emits a payment lifecycle event.

## Requirements

### REQ-1 — Submit a payment request
**User story:** As a calling service, I want to submit a payment request, so that
a payment can be authorized.

Acceptance criteria (EARS):
- WHEN a caller submits a payment request with a valid schema, THE service SHALL
  validate the request and return a payment identifier with status `AUTHORIZED`
  or `DECLINED`.
- IF the request fails schema validation, THEN THE service SHALL reject it with a
  `400` error describing the invalid fields.

### REQ-2 — Authentication and authorization
- WHEN a request arrives without a valid bearer token, THE service SHALL reject it
  with `401`.
- WHERE the token lacks the required scope, THE service SHALL reject the request
  with `403`.

### REQ-3 — Card data handling (PCI)
- THE service SHALL NOT log primary account numbers (PAN) or other cardholder data
  in plaintext.
- THE service SHALL mask sensitive fields before any response or log write.

### REQ-4 — Idempotency
- WHEN a caller resubmits a request with the same idempotency key within the
  retention window, THE service SHALL return the original result without creating
  a duplicate payment.

### REQ-5 — Event emission
- WHEN a payment reaches a terminal state (`AUTHORIZED`, `DECLINED`, `CAPTURED`),
  THE service SHALL emit a corresponding event to the approved event bus.
