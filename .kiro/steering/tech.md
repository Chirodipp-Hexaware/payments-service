# Tech

## Stack

- **Language:** Java 21 (LTS). Source/target 21.
- **Framework:** Spring Boot 3.2.5 + Spring Cloud Function 2023.0.1
  (`spring-cloud-function-context` + `spring-cloud-function-adapter-aws`).
- **Runtime:** AWS Lambda. No embedded web server —
  `spring.main.web-application-type: none`; Tomcat is excluded from the build.
- **Data:** AWS SDK v2 DynamoDB Enhanced Client (`dynamodb-enhanced`) with the
  `url-connection-client` HTTP client.
- **Auth:** Nimbus JOSE + JWT (`nimbus-jose-jwt` 9.37.3) for JWT scope parsing.
- **Validation:** Bean Validation (JSR-380) via `spring-boot-starter-validation`.
- **Logging:** Log4j2 (`spring-boot-starter-log4j2`); default Logback starter is
  excluded to avoid Lambda class-loading issues. Use SLF4J APIs in code.
- **JSON:** Jackson via `spring-boot-starter-json` (`ObjectMapper` is injected).
- **Test:** JUnit 5 + Mockito (`spring-boot-starter-test`, `mockito-core`,
  `mockito-junit-jupiter`).

Versions are pinned via BOMs (Spring Cloud, AWS SDK) and explicit properties.
Keep dependencies pinned; do not introduce open version ranges.

## Project location

The Maven project root is `src/` (i.e. `src/pom.xml`), and application code
lives under `src/src/main/java`. Run Maven from the `src/` directory.

## Build and test commands

Run from `src/`:

- **Build + test + coverage gate:** `mvn verify`
- **Compile only:** `mvn -q compile`
- **Unit tests only:** `mvn test`
- **Package the Lambda artifact:** `mvn package`

`mvn verify` is the authoritative gate. It runs Surefire (JUnit 5) and enforces
the **JaCoCo branch-coverage minimum of 80%** (BUNDLE-level). A build that drops
branch coverage below 0.80 fails. New/changed code must carry tests that keep
the gate green; a bug fix needs a test that fails before the fix and passes
after.

Never leave a test watch process running as part of automation — use single-run
invocations.

## Packaging / deployment

- The build produces a shaded ("fat") jar via the Maven Shade Plugin, attached
  with classifier **`aws`** (Spring Boot thin-launcher + shade). This is the
  artifact uploaded to Lambda.
- The Lambda handler is the Spring Cloud Function AWS adapter; the specific
  function is selected by `spring.cloud.function.definition`
  (`ordersSearchFunction;ordersSearchByOrderIdFunction`).
- Infrastructure (Lambda resource, env vars, API Gateway route, JWT authorizer,
  IAM) is provisioned externally (Terraform). Application code must not assume
  or hardcode resource names.

## Configuration

All environment-specific and sensitive values are injected via environment
variables — never committed.

- `ORDERS_TABLE_NAME` → `orders.table-name` (required). The DynamoDB Orders
  table name.
- `AWS_REGION` → `cloud.aws.region.static` (defaults to `us-east-1`).

Secrets are retrieved at runtime from the platform (env/secrets manager), never
from committed `.env` files.

## Conventions that bind code

- **Strict typing / immutability.** Prefer `final` fields and constructor
  injection (as the existing beans do). Validate all external input at the
  boundary before use.
- **Structured errors.** The function layer maps failures to HTTP status codes
  (400 validation, 401 auth, 500 unhandled) and returns a generic JSON error
  body. Do not leak stack traces or internal detail to clients; log detail
  server-side with context.
- **No plaintext PII in logs.** Log identifiers (`customerId`, `orderId`), never
  raw name/email/address. Mask via `PiiMaskingUtil` before any value leaves the
  service tier.
- **Bounded queries.** Respect the `limit` cap (fetch max 100) on DynamoDB reads.
- **Formatting** is enforced by the project formatter / lint-on-save hook — run
  it, don't hand-format.
