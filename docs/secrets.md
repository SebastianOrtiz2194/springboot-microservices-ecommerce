# Secrets Management

How this project handles secrets, and how to graduate from `.env` files to a real secret
manager. Local development uses `.env`; production should use **HashiCorp Vault** or
**AWS Secrets Manager**.

> Golden rule: secrets never live in Git. `.env` is gitignored; `.env.example` holds
> placeholders only; prod profile config uses `${VAR}` bindings with no fallback, so a
> missing secret **fails the app at startup** instead of running degraded.

---

## Level 1 — `.env` (current, local dev)

```bash
cp .env.example .env      # fill DB_PASSWORD, JWT_SECRET, AWS_* then:
docker compose up --build -d
```

- `docker-compose.yml` interpolates `${DB_PASSWORD}`, `${JWT_SECRET}`, `${AWS_*}` from `.env`
  and passes them as container environment variables.
- Bare-metal runs (`./mvnw spring-boot:run`) read the same variables from the shell.
- **Good for:** local dev, quick demos, CI smoke tests.
- **Not good for:** anything shared/production — no audit trail, no rotation, secrets visible
  in `docker inspect`, no access control.

---

## Level 2 — HashiCorp Vault (teach mode)

Vault is a secret store with an API, lease/TTL semantics, audit logging, and dynamic
credentials (it can generate short-lived DB passwords on demand — the endgame feature).

### 2.1 Run Vault locally (dev server)

```bash
docker run -d --name vault -p 8200:8200 \
  -e VAULT_DEV_ROOT_TOKEN_ID=root \
  hashicorp/vault:1.18
```

Dev mode is in-memory + auto-unsealed + fixed root token: perfect for learning,
**never** for production.

### 2.2 Store the JWT secret

```bash
export VAULT_ADDR=http://localhost:8200
export VAULT_TOKEN=root

vault kv put secret/ecommerce/config \
  jwt-secret="$(python -c 'import secrets; print(secrets.token_urlsafe(48))')" \
  db-password="local-dev-password"
```

### 2.3 Let Spring Boot read it — zero code changes

Add the dependency (already version-managed by the Spring Boot parent):

```xml
<dependency>
  <groupId>org.springframework.cloud</groupId>
  <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

Tell Spring where the secret lives in `application-prod.yml`:

```yaml
spring:
  config:
    import: vault://secret/ecommerce/config   # Boot 2.4+ import syntax
  cloud:
    vault:
      uri: ${VAULT_ADDR:http://localhost:8200}
      token: ${VAULT_TOKEN}
      kv:
        enabled: true
```

Keys map to properties: `jwt-secret` → `jwt.secret`, `db-password` → `db.password`.
So `app.jwt.secret` becomes `app.jwt.secret: ${jwt.secret}` — environment variables are
no longer needed for the secret itself.

### 2.4 Production auth: token → AppRole

A static `VAULT_TOKEN` in env vars is a starting point, not the destination. Production
pattern:

1. Vault admin creates a role bound to a policy that can only read `secret/ecommerce/*`.
2. The app authenticates with **AppRole** (`role-id` + short-lived `secret-id` injected by
   the platform, e.g. Kubernetes service account or CI job), gets a leased token, and
   Vault renews/rotates it automatically.
3. Spring config (prod):

```yaml
spring:
  cloud:
    vault:
      authentication: APPROLE
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
```

**Trade-offs:** operates a Vault cluster (HA, unseal keys, upgrades) or pays Vault Cloud;
in return you get audit logs, leases, rotation, and dynamic credentials for free.

---

## Level 3 — AWS Secrets Manager (teach mode)

Best when the platform already runs on AWS (ECS/EKS/EC2): IAM does the auth, no extra
server to operate.

### 3.1 Create the secret

```bash
aws secretsmanager create-secret \
  --name ecommerce/prod \
  --secret-string '{"jwtSecret":"REPLACE_ME","dbPassword":"REPLACE_ME"}'
```

### 3.2 Spring Boot integration

Dependency:

```xml
<dependency>
  <groupId>io.awspring.cloud</groupId>
  <artifactId>spring-cloud-aws-starter-secrets-manager</artifactId>
</dependency>
```

(`spring-cloud-aws-dependencies` BOM is already imported in the parent.)

`application-prod.yml`:

```yaml
spring:
  config:
    import: aws-secretsmanager:ecommerce/prod
  cloud:
    aws:
      region:
        static: ${AWS_REGION}
```

The JSON keys become properties (`jwtSecret` → `jwtSecret`), so map them:

```yaml
app:
  jwt:
    secret: ${jwt-secret}
```

Why the import maps JSON keys: Secrets Manager stores a JSON document; Spring Cloud AWS
flattens it into the Environment. Multi-key secrets keep IAM policies small.

### 3.3 Auth: IAM roles, not access keys

- **On AWS (ECS/EKS/EC2):** attach an IAM role/task-role. The SDK picks up credentials
  from the instance metadata service automatically — remove `AWS_ACCESS_KEY_ID` /
  `AWS_SECRET_ACCESS_KEY` from the environment entirely.
- **Outside AWS:** IAM user + long-lived keys is the fallback; rotate them and scope the
  policy to just `secretsmanager:GetSecretValue` on the one secret ARN.
- Least-privilege policy:

```json
{
  "Effect": "Allow",
  "Action": "secretsmanager:GetSecretValue",
  "Resource": "arn:aws:secretsmanager:us-east-1:123456789012:secret:ecommerce/prod-*"
}
```

### 3.4 Rotation

Enable automatic rotation with a Lambda (AWS provides RDS rotation templates). For the JWT
secret, rotation is a deploy-time event: rotate the value, roll the services, accept that
tokens signed with the old key are invalidated (or keep a key ring for graceful rotation —
a Phase-11+ enhancement).

---

## Comparison

| | `.env` | Vault | AWS Secrets Manager |
|---|---|---|---|
| Setup cost | none | run/operate a cluster (or Vault Cloud) | managed, pay per secret/API call |
| Auth to the store | file permissions | token/AppRole/leases | IAM roles (no static creds on AWS) |
| Rotation / leases | manual | built-in leases, dynamic DB creds | Lambda-based rotation |
| Audit | none | audit devices (every read logged) | CloudTrail |
| Best for | local dev, CI | multi-cloud / self-hosted prod | AWS-native prod |

**Recommended path for this project:** `.env` for local + CI → Secrets Manager (or Vault)
in prod, injected via `spring.config.import`, IAM/AppRole auth, no static secrets in
`docker-compose.yml` or environment.
