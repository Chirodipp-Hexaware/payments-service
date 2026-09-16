# Tasks — accept-payment

Implementation plan derived from `design.md`. Each task references the
requirement(s) it satisfies.

- [ ] 1. Scaffold CDK v2 app and TypeScript project structure under `src/`.
- [ ] 2. Define DynamoDB `payments` and `idempotency` tables (CDK). (REQ-1, REQ-4)
- [ ] 3. Define API Gateway HTTP API + JWT authorizer with `payments:write` scope. (REQ-2)
- [ ] 4. Implement request schema validation. (REQ-1)
- [ ] 5. Implement PII/cardholder masking before persist/log/response. (REQ-3)
- [ ] 6. Implement idempotency check/store keyed by `idempotencyKey`. (REQ-4)
- [ ] 7. Implement authorization call and status mapping. (REQ-1)
- [ ] 8. Emit lifecycle events to EventBridge on terminal state. (REQ-5)
- [ ] 9. Unit + integration tests to the 80% branch coverage floor.
- [ ] 10. Wire least-privilege IAM for the handler. (REQ-2, REQ-3)
