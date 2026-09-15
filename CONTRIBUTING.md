# Contributing

Conventions used in this repository. Follow them for every change so the codebase stays
consistent and reviewable.

## Commits

Conventional Commits: `type(scope): description` — lowercase, imperative, no period.

- `feat(users): add password reset endpoint`
- `fix(inventory): prevent oversell with atomic stock decrement`
- `test(product-service): add unit tests for S3 uploads`
- `chore(deps): update spring-boot to 3.3.x`
- `docs(api): annotate order controller`
- `build(test): enforce coverage gate`

Group unrelated changes into separate commits. Push only green builds (`mvn verify`).

## Code style

- **Spotless (Google Java Format, AOSP)** is enforced: run `mvn spotless:apply` before
  committing. `mvn verify` fails the build on violations (CI gate).
- One public type per file. 4-space indent. Max ~100 columns.
- Javadoc on all public types and methods (`@param` lowercase, no trailing period).

## Java / Spring Boot conventions

- **Records for DTOs.** Never expose JPA entities in the API layer.
- **Constructor injection only**, fields `private final`. No field `@Autowired`.
- **Package by feature** (`com.ecommerce.order.domain|dto|service|...`), not by layer.
- **`Optional` from `find*` methods**; translate empty to a named domain exception
  (`UserNotFoundException`, `ProductNotFoundException`, `OrderNotFoundException`).
- **Named unchecked exceptions** for domain errors; never `catch (Exception)` except in the
  global handler. All API errors go through `@RestControllerAdvice` as RFC 7807
  `ProblemDetail` — never hand-rolled error bodies in controllers.
- **Validation on DTOs** (`@Valid`, `@NotBlank`, `@Positive`, ...) plus `@Validated` +
  `@Positive` on `@PathVariable` IDs.
- **`@Transactional` at the finest granularity that keeps the unit of work atomic**
  (e.g. order creation + event publish; never hold a transaction across a network call
  longer than a bounded timeout).
- **SLF4J parameterized logging**: `log.info("create_order user_id={}", userId)`.
  Never string-concatenate in log statements.
- **No magic numbers/strings**: named constants or `app.*` configuration properties.
- **Immutability by default**: records, `final` fields, `List.copyOf`/`toList()`.

## Configuration & secrets

- `application.yml` holds non-secret defaults only. Real secrets come from env vars
  (see `.env.example`); `application-prod.yml` uses `${VAR:?message}` fail-fast bindings.
- Never commit `.env`. Never leave real credentials in `application-{dev,test}.yml`.
- Dev/test fallbacks (`changeme`, dev secrets, placeholder buckets) must be obviously
  non-production and fail loudly in prod profile.

## Messaging & caching

- Kafka events are the contract between services: **consumers own their schema**
  (`spring.json.use.type.headers: false` + `spring.json.value.default.type`), so producer
  refactors never break consumers. Never rely on Java type headers across services.
- Every consumer must be **idempotent** (dedupe key, e.g. `processed_order`) and combine it
  with `auto-offset-reset: earliest` for at-least-once delivery.
- Cache names are split per use (`product` vs `productList`); every mutation path evicts.
  Never cache unbounded `findAll()` results without TTL + pagination alternative.

## Resilience

- Downstream calls get a Resilience4j guard: circuit breaker for Kafka publishing (fallback
  rethrows → transaction rolls back → client gets 503 and retries), retry with backoff for
  transient I/O (S3), bulkhead for hot read paths. Validation failures are never retried.
- Every network client gets explicit timeouts (Kafka `max.block.ms`, gateway httpclient).

## Testing

- Pyramid: unit (Mockito, no Spring) → slices (`@WebMvcTest` with `@WithMockUser`,
  `@DataJpaTest` with Testcontainers Postgres) → integration (`@SpringBootTest` with real
  Postgres/Redis/Kafka containers).
- AssertJ (`isEqualByComparingTo` for `BigDecimal`), `method_scenario_expected` test names.
- JaCoCo gate: ≥70% line coverage on `service`, `auth`, `event` packages — meaningful
  coverage on business logic, not blind 100%.
- New bugs get a regression test before the fix when reproducible.

## Docs

- Every endpoint needs `@Operation` + `@ApiResponses` with real status codes; new error
  paths must document their `ProblemDetail` shape.
- Update `docs/openapi-*.yaml` (generated from live `/v3/api-docs.yaml`) and the Postman
  collection when the API changes.
