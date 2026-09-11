# E-Commerce Platform

[![CI](https://github.com/SebastianOrtiz2194/springboot-microservices-ecommerce/actions/workflows/ci.yml/badge.svg)](https://github.com/SebastianOrtiz2194/springboot-microservices-ecommerce/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-blue)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.3](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen)](https://spring.io/projects/spring-boot)

Microservices-based e-commerce backend built with Spring Boot, PostgreSQL, Redis, Kafka,
AWS S3, and Spring Cloud. Three business services behind an API Gateway with Eureka service
discovery, JWT auth, event-driven stock management, and full observability.

## Architecture

```
                            ┌─────────────────┐
                            │  Eureka (8761)  │
                            └────────┬────────┘
                                     │
     Client ──► ┌────────────────────▼───────────────────┐
                │   API Gateway (8080)                    │
                │   routing • timeouts • swagger aggregate │
                └───┬───────────────┬─────────────┬───────┘
                    │               │             │
          ┌─────────▼─────┐  ┌──────▼──────┐  ┌───▼─────────┐
          │ user-service  │  │product-svc  │  │order-service│
          │ (8081)        │  │ (8082)      │  │ (8083)      │
          │ Auth + JWT    │  │ Catalog     │  │ Orders      │
          │ Postgres      │  │ Postgres    │  │ Postgres    │
          │               │  │ Redis cache │  │             │
          └───────────────┘  └──────┬──────┘  └──────┬──────┘
                                    │                │
                            ┌───────▼────────────────▼──────┐
                            │       Kafka (events) KRaft    │
                            │ OrderPlaced → product-service │
                            └───────────────────────────────┘

     Observability: Actuator • Prometheus • Zipkin • custom business metrics
     Resilience: CircuitBreaker (order publish) • Retry (S3) • Bulkhead (reads)
```

## Tech Stack

| Layer        | Choice                                                        |
|--------------|---------------------------------------------------------------|
| Runtime      | Java 21, Spring Boot 3.3.4, Spring Cloud 2023.0.3             |
| Gateway      | Spring Cloud Gateway (reactive) + Eureka discovery            |
| Auth         | Spring Security, BCrypt, JWT access (15m) + refresh (7d)      |
| Data         | PostgreSQL per service, Flyway migrations (`ddl-auto: validate`) |
| Cache        | Redis (product catalog, 10m TTL, JSON serialization)          |
| Messaging    | Kafka KRaft, `order-placed` topic, idempotent consumer        |
| Storage      | AWS S3 (presigned URLs generated on read, never stored)       |
| Mapping      | MapStruct (compile-time entity ↔ DTO)                         |
| Docs         | springdoc-openapi, aggregated Swagger UI at the gateway       |
| Quality      | JUnit 5 + Mockito + AssertJ, `@WebMvcTest`/`@DataJpaTest` slices, Testcontainers, JaCoCo ≥70% on business logic, Spotless |

## Prerequisites

- Java 21+, Maven 3.9+ (or use `./mvnw`)
- PostgreSQL on `localhost:5432` with databases `user_db`, `product_db`, `order_db`
- Redis on `localhost:6379`
- Kafka on `localhost:9092` (KRaft, no Zookeeper)
- AWS credentials + S3 bucket (only for real image uploads)
- Docker Desktop (for Testcontainers-based tests; set
  `DOCKER_HOST=npipe:////./pipe/docker_engine` on Windows)

Copy the env template first:

```bash
cp .env.example .env   # .env is gitignored — never commit secrets
```

## Getting Started

Start infrastructure, then services in order (each in its own terminal):

```bash
# 1. Service Discovery
./mvnw spring-boot:run -pl service-discovery

# 2. API Gateway
./mvnw spring-boot:run -pl api-gateway

# 3. User Service
./mvnw spring-boot:run -pl user-service

# 4. Product Service
./mvnw spring-boot:run -pl product-service

# 5. Order Service
./mvnw spring-boot:run -pl order-service
```

Entry points:

- Eureka Dashboard: http://localhost:8761
- Gateway Swagger UI (all 3 APIs): http://localhost:8080/swagger-ui.html
- Per-service docs: `:8081`/`:8082`/`:8083` `/swagger-ui.html`
- Health: `/actuator/health` • Metrics: `/actuator/prometheus`

## Auth Flow

```bash
# Register (public) — saves tokens from the response
curl -X POST localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Alice","email":"alice@example.com","password":"s3cret-password"}'

# Login (public)
curl -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"alice@example.com","password":"s3cret-password"}'

# Refresh
curl -X POST localhost:8080/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refresh-token>"}'

# Authenticated request (USER or ADMIN for reads, ADMIN for writes)
curl localhost:8080/api/products \
  -H "Authorization: Bearer <access-token>"
```

Roles: `USER` (read catalog, manage own orders) and `ADMIN` (create users/products,
upload images). Seeded users: `john@example.com` / `jane@example.com` (password `changeme`
in dev seed data only — see Flyway migrations).

## API Endpoints

### Auth (`/api/auth/**`, public)

| Method | Endpoint           | Description       |
|--------|--------------------|-------------------|
| POST   | `/api/auth/register` | Register account, returns tokens |
| POST   | `/api/auth/login`    | Login, returns tokens            |
| POST   | `/api/auth/refresh`  | New access token from refresh token |

### Users (`/api/users/**`)

| Method | Endpoint          | Role  | Description    |
|--------|-------------------|-------|----------------|
| POST   | `/api/users`      | ADMIN | Create user    |
| GET    | `/api/users/{id}` | USER+ | Get user by ID |
| GET    | `/api/users`      | USER+ | List all users |

### Products (`/api/products/**`)

| Method | Endpoint                   | Role  | Description          |
|--------|----------------------------|-------|----------------------|
| POST   | `/api/products`            | ADMIN | Create product       |
| GET    | `/api/products/{id}`       | USER+ | Get product (fresh presigned image URL) |
| GET    | `/api/products`            | USER+ | List all products    |
| POST   | `/api/products/{id}/image` | ADMIN | Upload image (JPEG/PNG/WebP ≤5MB) |

### Orders (`/api/orders/**`, USER+)

| Method | Endpoint          | Description                              |
|--------|-------------------|------------------------------------------|
| POST   | `/api/orders`     | Create order, publishes `OrderPlacedEvent` |
| GET    | `/api/orders`     | List my orders                           |
| GET    | `/api/orders/{id}`| Get order by ID                          |

Full interactive docs with schemas and response codes: gateway Swagger UI.
Static specs: [`docs/openapi-user.yaml`](docs/openapi-user.yaml),
[`docs/openapi-product.yaml`](docs/openapi-product.yaml),
[`docs/openapi-order.yaml`](docs/openapi-order.yaml).
Postman: [`docs/postman_collection.json`](docs/postman_collection.json) (tokens auto-captured).

## Project Structure

```
ecommerce-platform/
├── service-discovery/   # Eureka Server (8761)
├── api-gateway/         # Spring Cloud Gateway (8080) + Swagger aggregation
├── user-service/        # Auth + users (8081)
│   └── com.ecommerce.user/
│       ├── auth/        # JwtUtil, AuthService, AuthController, DTOs
│       ├── domain/      # User entity
│       ├── dto/         # CreateUserRequest / UserResponse records
│       ├── mapper/      # MapStruct UserMapper
│       ├── config/      # SecurityConfig, JwtAuthFilter, OpenApiConfig
│       ├── exception/   # Domain exceptions + ProblemDetail handler
│       └── service|controller|repository
├── product-service/     # Catalog + stock (8082)
│   └── com.ecommerce.product/
│       ├── domain/      # Product (@Version), ProcessedOrder (dedupe)
│       ├── event/       # OrderEventConsumer (idempotent, atomic decrement)
│       ├── service/     # ProductService (Redis cache), S3Service (validated uploads)
│       └── config/      # RedisCacheConfig (+ hit/miss metrics), SecurityConfig
├── order-service/       # Orders + Kafka producer (8083)
│   └── com.ecommerce.order/
│       ├── domain/      # Order, OrderItem, OrderStatus
│       └── service/     # OrderService (@Transactional), OrderEventPublisher (circuit breaker)
├── docs/                # OpenAPI specs + Postman collection
└── pom.xml              # Parent: BOMs, Spotless, JaCoCo, Testcontainers
```

Packages are organized **by feature** (`user`, `product`, `order`), not by layer.

## Configuration

| Property | Location | Description |
|---|---|---|
| `spring.datasource.*` | all services | PostgreSQL (`DB_URL/DB_USERNAME/DB_PASSWORD`) |
| `spring.data.redis.*` | product-service | Redis (`REDIS_HOST/REDIS_PORT`) |
| `spring.kafka.*` | order/product | Broker (`KAFKA_BOOTSTRAP_SERVERS`) + timeouts |
| `app.jwt.secret` | all services | JWT signing key, min 32 bytes (`JWT_SECRET`) |
| `app.s3.*` | product-service | Bucket, presigned TTL, max file size |
| `resilience4j.*` | order/product | Circuit breaker, retry, bulkhead tuning |
| `management.*` | all + gateway | Actuator exposure, tracing sampling, Zipkin endpoint |
| `eureka.*` | all | Registry (`EUREKA_URL`), `prefer-ip-address: true` |

Profiles: default (local dev) • `dev` (verbose SQL) • `prod` (fail-fast, env required) •
`test` (containers, no tracing).

## Design Decisions (interview notes)

- **DTOs + MapStruct, never entities in APIs** — decouples persistence from contract.
- **RFC 7807 `ProblemDetail` globally** — one error shape everywhere, including 503 for
  downstream outages (retryable) vs 4xx for client errors.
- **Atomic `UPDATE ... WHERE stock >= qty`** + `@Version` — no oversell under concurrency;
  negative stock is impossible by construction, not by `if`.
- **Idempotent consumer + `earliest` offset** — at-least-once delivery without double
  decrement (`processed_order` dedupe), so redeploys never lose stock updates.
- **Consumer owns its event schema** (`use.type.headers: false` + default type) — producer
  package refactors can't break consumers; malformed records go to the error handler via
  `ErrorHandlingDeserializer` instead of poison-looping the partition.
- **Presigned URLs generated on read** — storing a 1h-signed URL would break images after
  expiry; the DB holds the S3 key.
- **Blocking bounded Kafka send + circuit breaker** — broker failure rolls the order
  transaction back (no phantom orders) and fails fast when the breaker is open.
- **Split Redis caches + explicit eviction** — single-product vs list caches never go stale
  after writes, uploads, or stock updates.
- **Bounded dedupe table** — `processed_order` rows are pruned nightly once they are older
  than Kafka could ever redeliver them (30d retention vs 7d broker retention), so the
  idempotency guard stays cheap forever.
- **Fail-fast secrets** — empty/weak JWT keys and missing prod env vars crash at startup
  with a clear message instead of obscure runtime errors.

## Testing

```bash
mvn verify              # unit + slices + Testcontainers integration + Spotless + JaCoCo gate
mvn test -pl order-service -Dtest=OrderServiceTest   # single class
```

- Pyramid: plain unit tests → `@WebMvcTest` (security matrix) → `@DataJpaTest` (Flyway on
  real Postgres) → `@SpringBootTest` integration (Postgres + Redis + Kafka containers).
- Requires Docker for container tests. Gate: JaCoCo ≥70% line coverage on
  `service`/`auth`/`event` packages.

See [CONTRIBUTING.md](CONTRIBUTING.md) for conventions.
