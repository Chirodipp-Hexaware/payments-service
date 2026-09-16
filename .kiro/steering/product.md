---
inclusion: always
---

# Product Context — payments-service

## What This Service Is

`payments-service` accepts and processes payment requests for the platform. It
exposes an internal API used by other services to authorize, capture, and query
payments, and emits payment lifecycle events for downstream consumers.

## Primary Users

- Other backend services (via the approved API layer) initiating payments.
- Platform/DevOps engineers operating and observing the service.
- (Read-only) Enterprise Architects reviewing generated `design.md` artifacts.

## Product Principles

- Every feature traces back to an explicit, testable requirement (EARS) with a
  linked entry under `.kiro/specs/`.
- Payment card data handling follows PCI scope — this service is treated as
  in-scope and inherits the org security baseline plus any PCI addendum.
- Public behavior changes (new endpoints, deprecations) follow a notice and
  sunset policy; no undocumented public API surface.

## Out of Scope

- This file describes the payments-service product context only. Org-wide product
  rules live in the synced org steering under `.kiro/steering/org/`.
