# Structure

## Repository layout

```
payments-service/
├── .kiro/
│   ├── steering/            # project steering (this folder)
│   │   └── org/             # SYNCED org standards — do NOT hand-edit
│   ├── specs/               # feature specs (requirements → design → tasks)
│   ├── hooks/               # agent hooks (lint/test/pan-scan on save)
│   └── settings/            # mcp.json, etc.
├── CODEOWNERS
└── src/                     # Maven project root (pom.xml lives here)
    ├── pom.xml
    └── src/
        ├── main/
        │   ├── java/com/arc/orderslambda/
        │   └── resources/application.yml
        └── test/java/com/arc/orderslambda/
```

Note the nested `src/src/...` — the Maven module root is the top-level `src/`
directory, and Maven's own `src/main` sits inside it. Run `mvn` from `src/`.

## Steering precedence

- Files directly under `.kiro/steering/` (`product.md`, `tech.md`,
  `structure.md`) are **project** steering — edit these freely.
- Files under `.kiro/steering/org/` are **synced** from the org knowledge base
  by the `kiro-sync` workflow. **Do not hand-edit them** — changes are
  overwritten. To change an org standard, PR the upstream `kiro-guardrails-dist`.
- Org standards (coding-standards, test-conventions, security, design-template)
  are the org-wide baseline. Project steering complements them; it never relaxes
  a security or coding-standard rule.

## Application package layout (`com.arc.orderslambda`)

Layered, one responsibility per package. Dependencies flow downward:
`function → service → repository → model`, with `auth`, `dto`, and `util` as
supporting packages.

- **`OrdersLambdaApplication`** — Spring Boot entry point. Bootstraps the context;
  Spring Cloud Function discovers the function beans via `application.yml`.
- **`function/`** — the Lambda boundary. `OrdersSearchFunction` and
  `OrdersSearchByOrderIdFunction` implement `Function<Map<String,Object>,
  Map<String,Object>>` over the API Gateway v2 HTTP event. Responsibilities:
  extract headers/query params, invoke the scope check, delegate to the service,
  serialize the response, and map exceptions to HTTP status codes. Bean names
  (`@Component("ordersSearchFunction")`) must match
  `spring.cloud.function.definition`.
- **`auth/`** — `ScopeValidator`. Parses the Bearer JWT and enforces the
  `orders:read` scope (defence-in-depth; edge authorizer verifies the signature).
  Handles both space-delimited and JSON-array `scope` claim forms.
- **`service/`** — `OrdersSearchService`. Business logic: Bean Validation of the
  request DTO, query orchestration, optional status filtering, `limit` bounding,
  and mapping `Order` → `OrderDto` **with PII masking applied here**.
- **`repository/`** — `OrderRepository`. DynamoDB Enhanced Client access.
  `findByCustomerId` queries the GSI `customerId-orderDate-index` (descending by
  `orderDate`); `findByOrderId` does a primary-key `GetItem`. Table name from
  `${orders.table-name}`.
- **`model/`** — `Order`, the `@DynamoDbBean` entity. Holds **raw** PII; the GSI
  constant `GSI_CUSTOMER_DATE` lives here. Never returned directly to clients.
- **`dto/`** — request/response contracts: `OrdersSearchRequestDto`,
  `OrdersSearchByOrderIdRequestDto` (Bean Validation annotations),
  `OrdersSearchResponseDto`, and `OrderDto` (the masked, client-facing shape).
- **`util/`** — `PiiMaskingUtil`, the shared masking helper (email, name,
  address). Null-safe, static, final. Use this for all PII masking — don't
  hand-roll.

## Where things go (conventions)

- **New endpoint / function:** add a `Function` bean in `function/`, register its
  bean name in `spring.cloud.function.definition` (`application.yml`), and route
  it in the infra config. Keep the handler thin — push logic into `service/`.
- **New business rule:** put it in `service/`, not in the function or repository.
- **New data access:** add methods to `repository/`; keep queries bounded and
  index-aware. No query logic in the service beyond post-fetch filtering.
- **Client-facing shape changes:** change `dto/`, never expose `model/` entities.
  Any new PII field must be masked in the service mapping via `PiiMaskingUtil`.
- **Config values:** add to `application.yml` bound to an env var; never hardcode
  resource names or secrets.

## Naming

- Files/packages: lowercase package names; types `PascalCase`; methods/vars
  `camelCase`; constants `UPPER_SNAKE_CASE` (per org coding-standards).
- Function bean names are `camelCase` and must exactly match the
  `spring.cloud.function.definition` entries.

## Tests

- Tests mirror the main package tree under `src/src/test/java/com/arc/orderslambda/`
  (e.g. `service/OrdersSearchServiceTest`).
- Unit tests mock collaborators (Mockito); follow Arrange–Act–Assert; keep them
  deterministic. Security-sensitive branches (auth failure, validation failure,
  PII masking) need explicit tests. See `org/test-conventions.md` for the full
  policy and the 80% branch-coverage floor enforced by `mvn verify`.
