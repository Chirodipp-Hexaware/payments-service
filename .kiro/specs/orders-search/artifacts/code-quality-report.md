# Code Quality Report — orders-search service

**Scope:** `src/main/java/com/arc/orderslambda/**` (13 source files) + existing test.
**Reviewed against:** org `coding-standards`, `security`, `test-conventions`, `tech`, `structure`, `product` steering.
**Date:** 2026-09-17
**Method:** Static read of all source, the one test, and `pom.xml`. `mvn verify` was **not** run (Maven is not installed in this environment), so coverage/build assertions below are reasoned from the code, not measured.

---

## 1. Summary

Overall the service is well-structured and closely follows the documented architecture: a thin function boundary, a service tier owning business logic and PII masking, an index-aware repository, and a null-safe masking utility. Constructor injection, `final` fields, Javadoc, and env-var-driven config are all consistent with the steering.

The most material problems are in **testing** and **duplication**, plus a **doc/behavior mismatch** in the masking utility. There are no hardcoded secrets, no string-built queries, and PII is masked in the service mapping — the security posture is sound.

| Area | Rating |
|---|---|
| Architecture / layering | Strong |
| Security (secrets, injection, PII) | Strong |
| Readability / conventions | Strong |
| Error handling | Good |
| Test coverage | Weak — likely fails the 80% gate |
| Duplication | Weak — two near-identical function classes |

---

## 2. Findings by severity

### High

**H1 — Test coverage almost certainly fails the JaCoCo 80% branch gate.**
`pom.xml` enforces `BRANCH ≥ 0.80` at BUNDLE level in `verify`. Only `OrdersSearchService` has a test (`OrdersSearchServiceTest`). There are **no tests** for `ScopeValidator`, `OrdersSearchFunction`, `OrdersSearchByOrderIdFunction`, or `PiiMaskingUtil`, even though the design doc (§10) lists all of them with specific scenarios. Given the branch density in `ScopeValidator` (string vs array scope, missing header, non-Bearer, empty token, parse failure) and the two functions (header extraction, limit parsing, status-code mapping), the bundle branch ratio is very unlikely to reach 0.80. **The build likely fails `mvn verify`.**

**H2 — Security-sensitive branches are untested — violates test-conventions.**
`test-conventions` requires explicit tests for auth and PII-masking failure branches. `ScopeValidator` (the `orders:read` enforcement) and `PiiMaskingUtil` (the masking that keeps PII from leaving the tier) have zero direct tests. These are exactly the branches the policy says must be covered 100%. This is a correctness-and-compliance gap, independent of the aggregate coverage number.

### Medium

**M1 — Duplicated function boilerplate.**
`OrdersSearchFunction` and `OrdersSearchByOrderIdFunction` are near-identical: `extractHeaders`, `successResponse`, `errorResponse`, `escapeJson`, and the HTTP status constants are copy-pasted verbatim, and the 3-step apply() flow (auth → parse → execute) is the same. This is duplicated logic that will drift. Extract a small shared base (e.g. an abstract `AbstractOrdersFunction` or a helper component) holding header extraction, response building, escaping, and the auth+error-mapping skeleton.

**M2 — `maskEmail` Javadoc example contradicts the implementation.**
The class Javadoc says `john.doe@example.com` → `****doe@example.com`. The code keeps the **last 4 characters** of the local part, so `john.doe` (8 chars) → `****.doe@example.com` (note the retained dot). The example drops the dot and is wrong. Either the comment or the intended rule is off. `coding-standards` says comments must be accurate; this one will mislead. (The service test uses `david.lee` → `*****.lee@corp.com`, which matches the code, so the code is internally consistent — only the doc example is wrong.)

**M3 — Undocumented over-fetch heuristic can under-return results.**
`OrdersSearchService.search` fetches `min(limit * 3, 100)` when a status filter is active, then filters and trims to `limit`. If more than `limit*3` (capped at 100) orders precede the matching ones, the response can contain **fewer than `limit`** matching orders even though more exist. This trade-off isn't described in the design's API/limit contract (§6/§9). Either document the behavior explicitly or switch to a DynamoDB `FilterExpression` (already flagged in design Open Questions).

### Low

**L1 — Redundant `limit` guard in the repository.**
`OrderRepository.findByCustomerId` passes `.limit(limit)` to the `QueryEnhancedRequest` and then also calls `.limit(limit)` on the result stream. The stream guard is harmless (comment says "safety guard"), but the double bound is slightly confusing. Keep it if defending against multi-page over-return, but the comment could state that intent more precisely.

**L2 — Two near-identical validation methods in the service.**
`validateRequest` and `validateByOrderIdRequest` differ only in the generic type. Consider a single generic `<T> void validate(T request)` helper to remove the duplication.

**L3 — `@SuppressWarnings("unchecked")` on methods that no longer need it.**
`extractHeaders` / `buildRequestDto` use `instanceof` pattern matching with wildcard maps and don't perform unchecked casts; the annotation on `apply` and these helpers appears unnecessary. Removing dead suppressions keeps the compiler honest (`coding-standards`: no dead code).

**L4 — `escapeJson` is a hand-rolled minimal escaper.**
Error bodies are built by string concatenation with a minimal escaper (handles `\ " \n \r`). It's adequate for the controlled messages here and safe against the obvious injection, but the success path already uses Jackson `ObjectMapper`. Building the error body via `objectMapper.writeValueAsString(Map.of("error", message))` would remove the custom escaper and cover all control characters uniformly.

**L5 — Log-level consistency.**
Repository logs table name at `INFO` on construction and query details at `DEBUG`; services log at `INFO` per request. All logged values are non-PII identifiers (`customerId`, `orderId`) — compliant with `security`/`product`. No action required; noted as verified.

---

## 3. What's done well (verified)

- **No hardcoded secrets or resource names.** Table name and region come from `ORDERS_TABLE_NAME` / `AWS_REGION` via `application.yml` (`security`, `tech`, REQ-9).
- **No query injection.** DynamoDB access uses the Enhanced Client with `Key`/`QueryConditional`; no string-built expressions (`security`).
- **PII masked at the service boundary.** `toDto` masks name/email/address via `PiiMaskingUtil`; the raw `Order` entity is never returned. Logs carry only identifiers (REQ-7, `product`).
- **Defence-in-depth auth.** `ScopeValidator` enforces `orders:read` and handles both space-delimited and JSON-array scope claims (REQ-6). Correctly documents that signature verification is the edge authorizer's job.
- **Structured error mapping.** Functions map validation → 400, auth → 401, unhandled → generic 500 with no stack trace leaked to the client; detail logged server-side (REQ-8, `coding-standards`).
- **Immutability / DI.** `final` fields, constructor injection, utility class with private constructor (`tech`, `coding-standards`).
- **Bounded reads.** Fetch capped at 100; response trimmed to `limit` (REQ-3).
- **Layering matches `structure`.** `function → service → repository → model` with `auth`/`dto`/`util` support packages; bean names match `spring.cloud.function.definition`.

---

## 4. Recommended actions (priority order)

1. **(H1/H2) Add the missing unit tests** for `ScopeValidator`, `PiiMaskingUtil`, and both function classes — the exact scenarios are already enumerated in design §10. Target 100% branch on the auth scope check and masking, then confirm `mvn verify` passes the 0.80 gate. A bug fix (M2) should ship with a test that pins the correct `maskEmail` behavior.
2. **(M1)** Extract shared function boilerplate into a common base/helper to remove duplication.
3. **(M2)** Fix the `maskEmail` Javadoc example (or the rule) so comment and code agree; add a masking test that locks it in.
4. **(M3)** Document the over-fetch/limit trade-off in the design's API contract, or move status filtering to a `FilterExpression`.
5. **(L1–L4)** Housekeeping: dedupe validation, drop unneeded `@SuppressWarnings`, consider Jackson for the error body, tighten the repository comment.

---

## 5. Caveats on this review

- This is a **static** review. I did not run `mvn verify`, the test suite, or JaCoCo — Maven is not available in this environment. The coverage/build claims (H1) are reasoned from the code and `pom.xml`, not measured. Run `mvn verify` from `src/` to confirm the gate result before acting on H1.
- Full WCAG/accessibility review is not applicable — this is a machine-to-machine API with no UI.
