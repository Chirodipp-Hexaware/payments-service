# Tasks — orders-search

Implementation plan derived from `design.md` and `requirements.md`. Each task
references the requirement(s) it satisfies.

> Status: this spec was reverse-engineered from code already present under
> `src/`. The tasks below are checked where the implementation already exists in
> the repository. Use them as the traceability record and as the checklist for
> verifying/extending the feature.

- [x] 1. Project scaffold — Spring Boot 3.2 + Spring Cloud Function (AWS adapter)
  on Java 21, Lambda packaging (shaded `aws` jar), no embedded web server.
  (`pom.xml`, `OrdersLambdaApplication`, `application.yml`)
- [x] 2. Define the `Order` DynamoDB entity with PK `orderId` and GSI
  `customerId-orderDate-index` (partition `customerId`, sort `orderDate`).
  (REQ-1, REQ-4) (`model/Order.java`)
- [x] 3. Resolve configuration from environment — `ORDERS_TABLE_NAME` and
  `AWS_REGION` (default `us-east-1`); no hardcoded names/secrets. (REQ-9)
  (`application.yml`, `repository/OrderRepository`)
- [x] 4. Implement `OrderRepository.findByCustomerId` — GSI query, newest-first
  (`scanIndexForward(false)`), limit-bounded. (REQ-1, REQ-3)
- [x] 5. Implement `OrderRepository.findByOrderId` — primary-key `GetItem`,
  returns `Optional`. (REQ-4)
- [x] 6. Define request/response DTOs with Bean Validation
  (`OrdersSearchRequestDto`, `OrdersSearchByOrderIdRequestDto`,
  `OrdersSearchResponseDto`, `OrderDto`). (REQ-1, REQ-4, REQ-5)
- [x] 7. Implement `PiiMaskingUtil` — null-safe masking for name, email, and
  shipping address. (REQ-7) (`util/PiiMaskingUtil`)
- [x] 8. Implement `OrdersSearchService.search` — validate request, query,
  case-insensitive status filter, bound to `limit` (fetch cap 100), map to masked
  `OrderDto`. (REQ-1, REQ-2, REQ-3, REQ-5, REQ-7)
- [x] 9. Implement `OrdersSearchService.searchByOrderId` — validate, `GetItem`,
  empty result when not found, masked `OrderDto`. (REQ-4, REQ-5, REQ-7)
- [x] 10. Implement `ScopeValidator` — Bearer parsing and `orders:read` scope
  enforcement, accepting string- and array-form `scope` claims. (REQ-6)
- [x] 11. Implement `OrdersSearchFunction` — header/query-param extraction, scope
  check, delegate to service, JSON response, status mapping (200/400/401/500),
  JSON-escaped error bodies. (REQ-1, REQ-2, REQ-3, REQ-6, REQ-8)
- [x] 12. Implement `OrdersSearchByOrderIdFunction` and register both beans in
  `spring.cloud.function.definition`. (REQ-4, REQ-6, REQ-8)
- [ ] 13. Tests to the 80% branch-coverage floor (JaCoCo, `mvn verify`):
  - [x] a. `OrdersSearchServiceTest` (search paths, filter, limit, lookup,
    validation). (REQ-1–REQ-5, REQ-7)
  - [ ] b. `ScopeValidatorTest` — missing header, non-Bearer, empty token,
    missing scope, string- and array-form scope. (REQ-6)
  - [ ] c. `PiiMaskingUtilTest` — email/name/address incl. null/blank and
    short-local-part boundaries. (REQ-7)
  - [ ] d. Function-layer tests — param parsing, non-integer `limit`, status-code
    mapping, JSON escaping. (REQ-3, REQ-6, REQ-8)
- [ ] 14. Confirm least-privilege IAM (read-only on the Orders table + GSI) in the
  external Terraform infra. (REQ-6, REQ-9)
- [ ] 15. Run `mvn verify` from `src/` and confirm the JaCoCo branch-coverage gate
  passes.
