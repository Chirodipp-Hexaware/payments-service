---
inclusion: always
---

# Technology Stack — payments-service

Project-local stack decisions. These sit under the org baseline in
`.kiro/steering/org/`; where the org standard is stricter, the org wins.

## Backend Stack

- Runtime: Node.js 20
- Language: TypeScript 5
- Infrastructure as Code: AWS CDK v2

## Approved AWS Services

- Compute: AWS Lambda
- API: Amazon API Gateway
- Data: Amazon DynamoDB, Amazon S3
- Eventing: Amazon EventBridge

Any AWS service outside this list requires an ADR before it may be provisioned.

## Dependency & Version Policy

- Pin dependency versions; no open ranges.
- Patch flagged CVEs within the org SLA (Critical 48h / High 7d / Medium 30d).
- Prefer AWS SDK for JavaScript v3; do not introduce v2.

## Testing

- Jest for unit/integration tests, run with `--run` (no watch mode in CI).
- 80% branch coverage floor on changed code (see org `test-conventions.md`).

## Performance Budget

- A change must not regress p95 latency by more than 10% for the affected route.
