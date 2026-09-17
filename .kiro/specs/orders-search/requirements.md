# Requirements — orders-search

Written in EARS format. Reverse-engineered from the implemented service code
under `src/src/main/java/com/arc/orderslambda`. Every requirement is explicit and
testable and maps to design elements in `design.md` and implementation items in
`tasks.md`.

> Note: this spec documents the **Orders Search** capability that the code in
> `src/` actually implements (Maven artifact `com.arc:orders-lambda`), despite the
> repository being named `payments-service`. See `.kiro/steering/product.md`.

## Introduction

The orders-search feature lets an authenticated, authorized caller retrieve a
customer's orders over an AWS Lambda HTTP API. It exposes two read-only
operations: a customer-scoped search with optional status filtering and a
paging limit, and a direct lookup of a single order by its identifier. All
personally identifiable information (PII) is masked before it leaves the service,
and every request must present a Bearer JWT carrying the `orders:read` scope.

## Requirements

### REQ-1 — Search orders by customer
**User story:** As a calling service, I want to search a customer's orders, so
that I can display their order history newest-first.

Acceptance criteria (EARS):
- WHEN a caller invokes `GET /orders/search` with a non-blank `customerId`, THE
  service SHALL return that customer's orders ordered by `orderDate` descending
  (most recent first).
- IF the `customerId` query parameter is absent or blank, THEN THE service SHALL
  reject the request with a `400` error indicating `customerId` is required.
- THE service SHALL wrap the results in a response object containing the list of
  order items and a count of the items returned.

### REQ-2 — Optional status filter
**User story:** As a calling service, I want to filter orders by status, so that
I can show only orders in a given state.

Acceptance criteria (EARS):
- WHERE a non-blank `status` query parameter is supplied, THE service SHALL
  return only orders whose status equals the supplied value, compared
  case-insensitively.
- WHERE the `status` parameter is absent or blank, THE service SHALL return
  orders regardless of status.

### REQ-3 — Result limit
**User story:** As a calling service, I want to bound the number of results, so
that responses stay small and predictable.

Acceptance criteria (EARS):
- WHERE a `limit` query parameter is absent, THE service SHALL default the limit
  to 20.
- IF the `limit` parameter is present but not a valid integer, THEN THE service
  SHALL reject the request with a `400` error indicating `limit` must be an
  integer.
- THE service SHALL return no more than `limit` order items to the caller.
- THE service SHALL bound the number of records fetched from the data store to a
  maximum of 100 regardless of the requested `limit`.

### REQ-4 — Look up a single order by id
**User story:** As a calling service, I want to fetch one order by its
identifier, so that I can retrieve a specific order quickly.

Acceptance criteria (EARS):
- WHEN a caller invokes `GET /orders/searchByOrderId` with a valid `orderId`, THE
  service SHALL return the matching order as a single-item result with a count of
  1.
- IF no order exists for the supplied `orderId`, THEN THE service SHALL return an
  empty result with a count of 0 (not an error).
- THE service SHALL retrieve the order via a direct primary-key lookup (no
  secondary-index scan).

### REQ-5 — Request validation
**User story:** As an operator, I want malformed requests rejected at the
boundary, so that invalid input never reaches the data store.

Acceptance criteria (EARS):
- THE service SHALL validate each request against its defined schema (Bean
  Validation) before processing.
- IF a request fails validation, THEN THE service SHALL reject it with a `400`
  error whose body describes the invalid field(s).
- THE service SHALL treat a null request as invalid and reject it.

### REQ-6 — Authentication and authorization
**User story:** As a security owner, I want every request authenticated and
scoped, so that only authorized callers can read orders.

Acceptance criteria (EARS):
- WHEN a request arrives without an `Authorization` header, or with a header that
  does not use the `Bearer` scheme, or with an empty/unparseable token, THE
  service SHALL reject it with a `401` error.
- WHERE the presented JWT does not carry the `orders:read` scope, THE service
  SHALL reject the request with a `401` error indicating insufficient scope.
- THE service SHALL accept the `scope` claim in either space-delimited string
  form or JSON-array form.

### REQ-7 — PII masking
**User story:** As a data-protection owner, I want customer PII masked, so that
sensitive data never leaves the service in plaintext.

Acceptance criteria (EARS):
- THE service SHALL mask customer name, email, and shipping address before
  including them in any response.
- THE service SHALL NOT log customer name, email, or shipping address in
  plaintext; log output SHALL be limited to non-PII identifiers such as
  `customerId` and `orderId`.
- THE service SHALL apply masking consistently to every order-returning
  operation (search and lookup-by-id).

### REQ-8 — Error handling and responses
**User story:** As a calling service, I want predictable, safe error responses,
so that I can handle failures without seeing internal detail.

Acceptance criteria (EARS):
- WHEN a request succeeds, THE service SHALL respond with status `200`, a
  `Content-Type: application/json` header, and a JSON body.
- IF an unhandled error occurs during processing, THEN THE service SHALL respond
  with a `500` error and a generic message, without leaking stack traces or
  internal detail to the caller.
- THE service SHALL return error bodies as JSON with the offending value safely
  escaped.

### REQ-9 — Configuration
**User story:** As an operator, I want environment-specific values injected, so
that no resource names or secrets are hardcoded.

Acceptance criteria (EARS):
- THE service SHALL resolve the orders table name from the `ORDERS_TABLE_NAME`
  environment variable at runtime.
- THE service SHALL resolve the AWS region from the `AWS_REGION` environment
  variable, defaulting to `us-east-1` when it is not set.
- THE service SHALL NOT contain hardcoded resource names, credentials, or secrets
  in source.
