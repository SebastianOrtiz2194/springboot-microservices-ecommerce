# E-Commerce Platform

Microservices-based e-commerce backend built with Spring Boot, PostgreSQL, Redis, AWS S3, and Spring Cloud.

## Tech Stack

- **Java 21** / **Spring Boot 3.3.4** / **Spring Cloud 2023.0.3**
- **Netflix Eureka** — Service Discovery
- **Spring Cloud Gateway** — API Gateway
- **PostgreSQL** — Database (per-service)
- **Redis** — Caching (product-service)
- **AWS S3** — Product image storage
- **Maven** — Build tool

## Architecture

```
                        ┌───────────────────────┐
                        │   Eureka Discovery    │
                        │       (8761)          │
                        └──────────┬────────────┘
                                   │
                ┌──────────────────┼──────────────────┐
                │                  │                  │
    ┌───────────▼──────────┐      │      ┌───────────▼──────────┐
    │    API Gateway       │      │      │    Product Service   │
    │       (8080)         │      │      │       (8082)         │
    │                      │      │      │  PostgreSQL + Redis   │
    │  /api/users/**   ────┼──────┼──────┤  + AWS S3            │
    │  /api/products/** ───┼──────┘      └──────────────────────┘
    └──────────────────────┘
                │
    ┌───────────▼──────────┐
    │    User Service      │
    │       (8081)         │
    │    PostgreSQL         │
    └──────────────────────┘
```

## Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL (running on `localhost:5432`)
- Redis (running on `localhost:6379`)
- AWS account with S3 bucket (for image uploads)

## Getting Started

### 1. Create databases

```sql
CREATE DATABASE user_db;
CREATE DATABASE product_db;
```

### 2. Start services (in order)

```bash
# 1. Service Discovery
cd service-discovery && mvn spring-boot:run

# 2. API Gateway
cd api-gateway && mvn spring-boot:run

# 3. User Service
cd user-service && mvn spring-boot:run

# 4. Product Service
cd product-service && mvn spring-boot:run
```

### 3. Access

- **Eureka Dashboard:** http://localhost:8761
- **API Gateway:** http://localhost:8080

## API Endpoints

### User Service

| Method | Endpoint              | Description     |
|--------|-----------------------|-----------------|
| POST   | `/api/users`          | Create user     |
| GET    | `/api/users/{id}`     | Get user by ID  |
| GET    | `/api/users`          | List all users  |

### Product Service

| Method | Endpoint                     | Description         |
|--------|------------------------------|---------------------|
| POST   | `/api/products`              | Create product      |
| GET    | `/api/products/{id}`         | Get product by ID   |
| GET    | `/api/products`              | List all products   |
| POST   | `/api/products/{id}/image`   | Upload product image|

## Project Structure

```
ecommerce-platform/
├── service-discovery/     # Eureka Server (port 8761)
├── api-gateway/           # Spring Cloud Gateway (port 8080)
├── user-service/          # User management (port 8081)
│   ├── model/User.java
│   ├── repository/UserRepository.java
│   └── controller/UserController.java
├── product-service/       # Product catalog (port 8082)
│   ├── model/Product.java
│   ├── repository/ProductRepository.java
│   ├── controller/ProductController.java
│   └── service/S3Service.java
└── pom.xml                # Parent POM
```

## Configuration

Each service uses `application.yml` for configuration. Key settings:

| Property | Location | Description |
|---|---|---|
| `spring.datasource.*` | `user-service`, `product-service` | PostgreSQL connection |
| `spring.data.redis.*` | `product-service` | Redis cache config |
| `cloud.aws.s3.*` | `product-service` | AWS S3 bucket/region |
| `eureka.client.service-url.*` | All services | Eureka registry URL |
