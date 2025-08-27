# springboot-microservices-ecommerce
E-commerce backend built with Spring Boot microservices, PostgreSQL, Redis, AWS S3, and Spring Cloud.

# 🛒 E-Commerce Backend with Spring Boot Microservices

A **microservices-based e-commerce backend** built with modern Spring Boot practices, including service discovery, API gateway, caching, and cloud file storage.

## 🚀 Tech Stack
- **Java 21 + Spring Boot 3**
- **Spring Cloud Netflix Eureka** – Service discovery
- **Spring Cloud Gateway** – API Gateway
- **PostgreSQL** – Relational database (user & product DBs)
- **Redis** – Distributed cache
- **AWS S3** – File storage for product images
- **Maven** – Build & dependency management

## 🧩 Microservices Architecture
- **Eureka Server** – Registers & discovers services
- **API Gateway** – Single entrypoint for all requests
- **User Service** – Manages user accounts (CRUD)
- **Product Service** – Manages products, integrates with Redis & S3

