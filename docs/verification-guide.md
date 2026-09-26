# Verification Guide — check every endpoint step by step

A complete, copy-pasteable walkthrough of the platform: start the stack, verify the
infrastructure, authenticate, then call **every one of the 13 API endpoints** with real
request bodies and the exact responses you should see. All responses below were captured
from a live `docker compose up` run.

Two ways to follow along:

- **curl** — the examples in this document (works in bash, Git Bash, WSL; PowerShell users:
  use `Invoke-RestMethod` or run them from `curl.exe`).
- **Postman** — import [`docs/postman_collection.json`](postman_collection.json). It contains
  the same calls plus assertions, so the Postman **Runner** shows pass/fail per request.

---

## 0. Endpoint and auth map

All API traffic goes through the **API Gateway** (`http://localhost:8080`). Authentication
is a JWT bearer token obtained from `/api/auth/login` or `/api/auth/register`.

| # | Method | Endpoint | Who can call it | Body |
|---|--------|----------|-----------------|------|
| 1 | POST | `/api/auth/register` | public | JSON |
| 2 | POST | `/api/auth/login` | public | JSON |
| 3 | POST | `/api/auth/refresh` | public | JSON |
| 4 | POST | `/api/users` | ADMIN | JSON |
| 5 | GET | `/api/users/{id}` | USER or ADMIN | — |
| 6 | GET | `/api/users` | USER or ADMIN | — |
| 7 | POST | `/api/products` | ADMIN | JSON |
| 8 | GET | `/api/products/{id}` | USER or ADMIN | — |
| 9 | GET | `/api/products` | USER or ADMIN | — |
| 10 | POST | `/api/products/{id}/image` | ADMIN | multipart file |
| 11 | POST | `/api/orders` | USER or ADMIN | JSON |
| 12 | GET | `/api/orders` | USER or ADMIN | — |
| 13 | GET | `/api/orders/{id}` | USER or ADMIN | — |

Roles: `USER` — read catalog, place orders, list own orders. `ADMIN` — everything `USER`
can do plus create users/products and upload images.

> Commands below assume bash and export two variables first:
> ```bash
> export BASE=http://localhost:8080
> export EMAIL="you-$(date +%s)@example.com"   # unique per run
> ```

---

## 1. Start the platform

```bash
cp .env.example .env        # set DB_PASSWORD, JWT_SECRET, AWS_* (uploads only)
docker compose up --build -d
docker compose ps           # all services with a healthcheck must say "healthy"
```

Expected: `service-discovery`, `api-gateway`, `user-service`, `product-service`,
`order-service`, `postgres-*` ×3, `kafka`, `redis`, `zipkin` all `(healthy)`;
`grafana`/`prometheus` show `Up` (they define no healthcheck). First build takes several
minutes (Maven runs inside Docker).

---

## 2. Infrastructure checks (no authentication)

### 2.1 Eureka readiness gate — `GET :8761/actuator/health`

```bash
curl -s http://localhost:8761/actuator/health
```
```json
{"status":"UP","components":{"diskSpace":{"status":"UP", ...},"ping":{"status":"UP"}, ...}}
```

This is the endpoint the compose healthcheck polls; four services wait for it before starting.

### 2.2 Gateway health — `GET /actuator/health`

```bash
curl -s $BASE/actuator/health
```
```json
{"status":"UP", ...}
```

### 2.3 Prometheus scrape — `GET :8761/actuator/prometheus`

```bash
curl -s http://localhost:8761/actuator/prometheus | head -5
```
```
# HELP application_ready_time_seconds Time taken for the application to be ready to service requests
# TYPE application_ready_time_seconds gauge
application_ready_time_seconds{...} 5.329
```

### 2.4 Dashboards (browser)

| What | URL | Credentials |
|---|---|---|
| Swagger UI (all 3 APIs aggregated) | http://localhost:8080/swagger-ui.html | — |
| Eureka dashboard | http://localhost:8761 | — |
| Grafana | http://localhost:3000 | admin / admin (or `GRAFANA_PASSWORD`) |
| Prometheus | http://localhost:9090 | — |
| Zipkin traces | http://localhost:9411 | — |

---

## 3. Authentication (all public)

### 3.1 Register — `POST /api/auth/register`

**Auth:** none. **Body:**

```json
{
  "name": "Alice",
  "email": "you-1727340000@example.com",
  "password": "s3cret-password"
}
```

```bash
curl -s -X POST $BASE/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"Alice\",\"email\":\"$EMAIL\",\"password\":\"s3cret-password\"}"
```

**Response `201 Created`:**

```json
{
  "accessToken": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJ5b3Ut...InVzZXJJZCI6OCwicm9sZSI6IlVTRVIiLCJpYXQiOjE3OTA0MTMzNzYsImV4cCI6MTc5MDQxNDI3Nn0...",
  "refreshToken": "eyJhbGciOiJIUzM4NCJ9...",
  "tokenType": "Bearer"
}
```

The access token is an **HS384 JWT** with claims `sub` (email), `userId`, `role`, `iat`,
`exp` (15 min). The refresh token lives 7 days. Save both:

```bash
ACCESS=$(curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"name\":\"Alice\",\"email\":\"$EMAIL\",\"password\":\"s3cret-password\"}" \
  | python -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
```

### 3.2 Login — `POST /api/auth/login`

**Auth:** none. **Body:**

```json
{ "email": "you-1727340000@example.com", "password": "s3cret-password" }
```

```bash
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"s3cret-password\"}"
```

**Response `200 OK`:** same shape as register (`accessToken`, `refreshToken`, `tokenType`).
Wrong email or password → `404` (see §7).

### 3.3 Refresh — `POST /api/auth/refresh`

**Auth:** none (the token itself is the credential). **Body:**

```json
{ "refreshToken": "<refresh-token-from-register-or-login>" }
```

```bash
curl -s -X POST $BASE/api/auth/refresh -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

**Response `200 OK`:** a **new** `accessToken` (15 min) and the same `refreshToken`.

### 3.4 Use the token

Every protected call needs the header:

```
Authorization: Bearer <accessToken>
```

```bash
curl -s $BASE/api/products -H "Authorization: Bearer $ACCESS"
```

### 3.5 Get an ADMIN user (needed for the write endpoints)

`/api/auth/register` always creates a `USER`, and `POST /api/users` requires `ADMIN` — so
promote one account directly in the database (dev/demo environments only):

```bash
docker compose exec postgres-user psql -U postgres -d user_db \
  -c "UPDATE users SET role='ADMIN' WHERE email='$EMAIL';"
```

Then **log in again** — the `role` claim is baked into the JWT at issue time, so a token
issued before the promotion still carries `USER`:

```bash
curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d "{\"email\":\"$EMAIL\",\"password\":\"s3cret-password\"}"
```

> In production, admins would be provisioned by an ops process, not self-registered.

---

## 4. Users API (`/api/users`)

### 4.1 List users — `GET /api/users`

**Auth:** `USER` or `ADMIN`. **Body:** none.

```bash
curl -s $BASE/api/users -H "Authorization: Bearer $ACCESS"
```

**Response `200 OK`:**

```json
[
  { "id": 1, "name": "John Doe", "email": "john@example.com", "role": "USER" },
  { "id": 2, "name": "Jane Smith", "email": "jane@example.com", "role": "USER" }
]
```

### 4.2 Get user by id — `GET /api/users/{id}`

**Auth:** `USER` or `ADMIN`.

```bash
curl -s $BASE/api/users/1 -H "Authorization: Bearer $ACCESS"
```

**Response `200 OK`:** `{ "id": 1, "name": "John Doe", "email": "john@example.com", "role": "USER" }`
— the `password` field is never serialized. Unknown id → `404` (see §7).

### 4.3 Create user — `POST /api/users`

**Auth:** `ADMIN`. **Body:**

```json
{
  "name": "Bob",
  "email": "bob@example.com",
  "password": "s3cret-password",
  "role": "USER"
}
```

```bash
curl -s -X POST $BASE/api/users -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"name":"Bob","email":"bob@example.com","password":"s3cret-password","role":"USER"}'
```

**Response `201 Created`:** `{ "id": 8, "name": "Bob", "email": "bob@example.com", "role": "USER" }`

---

## 5. Products API (`/api/products`)

### 5.1 List products — `GET /api/products`

**Auth:** `USER` or `ADMIN`.

```bash
curl -s $BASE/api/products -H "Authorization: Bearer $ACCESS"
```

**Response `200 OK`** (catalog is seeded with 5 products):

```json
[
  { "id": 1, "name": "Wireless Headphones", "description": "Noise-cancelling Bluetooth headphones with 30-hour battery", "price": 79.99, "stockQuantity": 93, "imageUrl": null },
  ...
]
```

### 5.2 Get product by id — `GET /api/products/{id}`

**Auth:** `USER` or `ADMIN`.

```bash
curl -s $BASE/api/products/1 -H "Authorization: Bearer $ACCESS"
```

**Response `200 OK`:**

```json
{ "id": 1, "name": "Wireless Headphones", "description": "Noise-cancelling Bluetooth headphones with 30-hour battery", "price": 79.99, "stockQuantity": 93, "imageUrl": null }
```

`imageUrl` is a **fresh pre-signed S3 URL** generated on read when the product has an
uploaded image (`null` otherwise); it expires after `app.s3.presigned-duration` (default 1h).

### 5.3 Create product — `POST /api/products`

**Auth:** `ADMIN`. **Body** (`stockQuantity` optional, defaults to `0`):

```json
{
  "name": "USB-C Hub",
  "description": "7-in-1 USB-C hub with HDMI",
  "price": 34.99,
  "stockQuantity": 25
}
```

```bash
curl -s -X POST $BASE/api/products -H "Authorization: Bearer $ADMIN_ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"name":"USB-C Hub","description":"7-in-1 USB-C hub with HDMI","price":34.99,"stockQuantity":25}'
```

**Response `201 Created`:**

```json
{ "id": 6, "name": "USB-C Hub", "description": "7-in-1 USB-C hub with HDMI", "price": 34.99, "stockQuantity": 25, "imageUrl": null }
```

### 5.4 Upload product image — `POST /api/products/{id}/image`

**Auth:** `ADMIN`. **Body:** multipart/form-data, field `file` (JPEG/PNG/WebP, max 5 MB).
Requires real `AWS_*` / `S3_BUCKET_NAME` values in `.env`.

```bash
curl -s -X POST $BASE/api/products/1/image -H "Authorization: Bearer $ADMIN_ACCESS" \
  -F "file=@./headphones.jpg;type=image/jpeg"
```

**Response `200 OK`:**

```json
{ "imageUrl": "https://your-bucket.s3.amazonaws.com/<uuid>-headphones.jpg?X-Amz-Signature=..." }
```

Only the S3 **key** is stored; the pre-signed URL is regenerated on every read (§5.2).

---

## 6. Orders API (`/api/orders`)

### 6.1 Create order — `POST /api/orders`

**Auth:** `USER` or `ADMIN`. **Body** (each item needs `productId`, `productName`,
`quantity` ≥ 1, `unitPrice` > 0; the list must not be empty):

```json
{
  "items": [
    { "productId": 1, "productName": "Wireless Headphones", "quantity": 2, "unitPrice": 79.99 },
    { "productId": 2, "productName": "Mechanical Keyboard", "quantity": 1, "unitPrice": 149.99 }
  ]
}
```

```bash
curl -s -X POST $BASE/api/orders -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"items":[{"productId":1,"productName":"Wireless Headphones","quantity":2,"unitPrice":79.99}]}'
```

**Response `201 Created`:**

```json
{
  "id": 5,
  "userId": 10,
  "totalAmount": 79.99,
  "status": "CREATED",
  "createdAt": "2026-09-26T09:05:41.5040204",
  "items": [
    { "id": 5, "productId": 1, "productName": "Wireless Headphones", "quantity": 1, "unitPrice": 79.99 }
  ]
}
```

Side effect: an `OrderPlacedEvent` is published to the Kafka topic `order-placed`.

### 6.2 List my orders — `GET /api/orders`

**Auth:** `USER` or `ADMIN`. Returns only the caller's orders (`userId` comes from the JWT).

```bash
curl -s $BASE/api/orders -H "Authorization: Bearer $ACCESS"
```

**Response `200 OK`:** array of the same order objects as §6.1.

### 6.3 Get order by id — `GET /api/orders/{id}`

**Auth:** `USER` or `ADMIN`.

```bash
curl -s $BASE/api/orders/5 -H "Authorization: Bearer $ACCESS"
```

**Response `200 OK`:** single order object. Unknown id → `404`.

### 6.4 Verify the async stock decrement (Kafka flow)

The order endpoint answers immediately; `product-service` consumes the event and decrements
stock asynchronously (normally within a second). Check it:

```bash
curl -s $BASE/api/products/1 -H "Authorization: Bearer $ACCESS" | grep stockQuantity   # before
# ... create an order for product 1, quantity 2 ...
sleep 3
curl -s $BASE/api/products/1 -H "Authorization: Bearer $ACCESS" | grep stockQuantity   # after: -2
```

The decrement runs as `UPDATE ... WHERE stock >= qty`, so it can never oversell, and each
`orderId` is processed exactly once (dedupe table `processed_order`).

---

## 7. Error contract (verified live responses)

Success responses are plain JSON DTOs; error responses are **RFC 7807 `ProblemDetail`**:

| Case | Status | Response body |
|---|---|---|
| Register with an existing email | `409` | `{"type":"about:blank","title":"Conflict","status":409,"detail":"Email is already registered","instance":"/api/auth/register"}` |
| Order with empty `items` | `400` | `{"type":"about:blank","title":"Validation failed","status":400,"detail":"items: must not be empty","instance":"/api/orders"}` |
| Register with blank `name` | `400` | `{"type":"about:blank","title":"Validation failed","status":400,"detail":"name: must not be blank","instance":"/api/auth/register"}` |
| Refresh with a bogus token | `400` | `{"type":"about:blank","title":"Bad request","status":400,"detail":"Invalid or expired refresh token","instance":"/api/auth/refresh"}` |
| Login with wrong password / unknown email | `404` | `{"type":"about:blank","title":"User not found","status":404,"detail":"User not found with email: ...","instance":"/api/auth/login"}` |
| `GET /api/users/99999` | `404` | `{"type":"about:blank","title":"User not found","status":404,"detail":"User not found with id: 99999","instance":"/api/users/99999"}` |
| Write endpoint as `USER` (e.g. create product) | `403` | `{"timestamp":"...","status":403,"error":"Forbidden","path":"/api/products"}` |
| Request with **no** token | `403` | `{"timestamp":"...","status":403,"error":"Forbidden","path":"/api/products"}` |

> **Known inconsistency (follow-up item):** anonymous/forbidden requests are rejected inside
> the Spring Security filter chain, so they return the framework's default error body with
> `403` instead of a `401`/`403` `ProblemDetail`. Everything handled by the application
> layer is RFC 7807. Unifying the filter-chain responses is a pending hardening item.

Quick reproductions:

```bash
# 409 duplicate email
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"name\":\"Alice\",\"email\":\"$EMAIL\",\"password\":\"s3cret-password\"}"
# 400 empty order
curl -s -X POST $BASE/api/orders -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' -d '{"items":[]}'
# 403 no token
curl -s $BASE/api/products
```

---

## 8. Postman collection

1. Postman → **Import** → [`docs/postman_collection.json`](postman_collection.json).
2. Set nothing: `baseUrl` defaults to `http://localhost:8080`; `Register` generates a unique
   `{{userEmail}}` per run and stores `{{accessToken}}` / `{{refreshToken}}` automatically;
   created `{{userId}}`, `{{productId}}`, `{{orderId}}` are captured too and reused by the
   follow-up requests.
3. Run folders **in order**: `0. Infrastructure → 1. Auth → 2. Users → 3. Products →
   4. Orders → 5. Guards → 6. Docs`.
4. Use the **Collection Runner** for a one-click pass/fail report — every request carries a
   status assertion (e.g. `Register` must return 201 and issue tokens; `Guards` must return
   409/400/403).

ADMIN-only requests (`Create user`, `Create product`, `Upload image`) return `403` until the
account behind `{{accessToken}}` is promoted (§3.5) and you re-run `1. Auth → Login`.

---

## 9. Cleanup

```bash
docker compose down        # stop, keep data
docker compose down -v     # stop and wipe databases, Kafka, Grafana state
```

## Checklist

- [ ] `docker compose ps` — all healthchecked services `healthy`
- [ ] `:8761/actuator/health` UP, `:8761/actuator/prometheus` serves metrics
- [ ] Register + login + refresh all return tokens; refresh issues a new access token
- [ ] Duplicate register → 409; empty order → 400; no token → 403; bad login → 404
- [ ] Users: list + get work as USER; create works as ADMIN
- [ ] Products: list + get work as USER; create + image upload work as ADMIN
- [ ] Orders: create → appears in `GET /api/orders`; stock drops within seconds (Kafka)
- [ ] Swagger UI at `:8080/swagger-ui.html` lists all three APIs
