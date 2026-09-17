# Product

## What this is

The **orders-lambda** service (Maven artifact `com.arc:orders-lambda`) is a
serverless read API for customer orders. It runs as an AWS Lambda function
fronted by an API Gateway v2 HTTP API and backed by a DynamoDB Orders table.

Despite the repository name `payments-service`, the code in `src/` implements
the **Orders Search** capability. Treat "orders-lambda" as the authoritative
component name.

## What it does

Two read-only operations, wired as Spring Cloud Function beans:

- **Orders search** (`ordersSearchFunction` → `GET /orders/search`) — returns a
  customer's orders, newest first, with an optional `status` filter and a
  `limit`. Backed by the DynamoDB GSI `customerId-orderDate-index`.
- **Order lookup by id** (`ordersSearchByOrderIdFunction` →
  `GET /orders/searchByOrderId`) — a direct primary-key `GetItem` on `orderId`,
  targeting a ≤ 10 ms p95 latency budget.

## Who uses it

Internal/authorized service clients calling over the HTTP API. Every request
carries a Bearer JWT. The route is protected by an API Gateway JWT Authorizer
(signature/expiry/audience verified at the edge), and the Lambda performs a
defence-in-depth scope check requiring the `orders:read` scope.

## Value and non-negotiables

- **Privacy by default.** Customer PII (name, email, shipping address) is stored
  raw but **always masked before it leaves the service tier**. No endpoint
  returns unmasked PII, and PII must never be logged in plaintext.
- **Least privilege and no hardcoded config.** Resource names and environment
  specifics (e.g. the Orders table) come from environment variables at runtime,
  never from committed source.
- **Predictable, bounded reads.** Result sets are capped (`limit`, max 100
  fetched) to keep latency and cost bounded.

## Scope

Read paths only today (search + lookup). There is no write/mutation path in this
service. Keep new work aligned to the read model unless a spec explicitly
introduces writes.
