# Wallet App (Kotlin + Spring Boot)

## Overview

This repository contains the backend code for a Wallet Application built with Kotlin and Spring Boot WebFlux (reactive). The application
supports basic wallet transactions:

- Deposit: Users can deposit money into their wallets.
- Withdraw: Users can withdraw money from their wallets.
- Transfer: Users can send money to another user's wallet.
- Balance Check: Users can check their wallet balance.
- Transaction History: Users can view their transaction history.

The app uses a centralized PostgreSQL database and exposes a REST API for managing users, wallets, and transactions.

---

## Requirements

- JDK 17
- Kotlin 2.x toolchain via Gradle (configured by the project)
- Docker Desktop (for local PostgreSQL)
- PostgreSQL 14.x (if running DB manually)

---

## TL;DR

### Run the application locally:

1) Start Docker Desktop (for local PostgreSQL).
2) Start a local PostgreSQL:
   ```bash
   docker run --name fc-postgres -e POSTGRES_PASSWORD=password -e POSTGRES_USER=user -e POSTGRES_DB=wallet_db -p 5432:5432 -d postgres:14.18
   ```
3) Run the application:
   ```bash
   ./gradlew bootRun
   ```
   The server starts on port 8080 by default.

### Run the tests:

- Unit tests:
  ```bash
  ./gradlew test
  ```
- Integration tests (H2):
  ```bash
  ./gradlew integrationTest
  ```
- End-to-end tests (H2):
  ```bash
  ./gradlew e2eTest
  ```
  
---

## HTTP API Endpoints

Base URL: `http://localhost:8080`

- User
    - POST `/users` — Create a new user.
    - GET `/users/{userId}/transactions` — Get transaction history for a specific user.

- Wallet
    - GET `/users/{userId}/wallet` — Retrieve wallet info, including balance for a specific user.
    - POST `/users/{userId}/wallet/deposit` — Deposit money into a user's wallet.
    - POST `/users/{userId}/wallet/withdraw` — Withdraw money from a user's wallet.
    - POST `/users/{userId}/wallet/transfer` — Transfer money from one user's wallet to another.

Notes:

- Amounts are represented as integer minor units (e.g., cents or milli-units) to avoid floating-point precision issues.
- Typical request/response payloads use JSON.

---

## Technologies Used

- Kotlin (JVM) + Spring Boot (Spring WebFlux)
- PostgreSQL
- H2 (for integration and E2E tests)
- Docker (for local DB)
- Gradle Kotlin DSL
- Java 17 (runtime and toolchain)

---

## Design Decisions and Highlights

1) Monetary values as integer minor units (e.g., cents)
    - Avoid precision issues from floating-point arithmetic.
    - Use Long for fast arithmetic and predictable behavior.
    - Entire system should consistently treat balances as minor units.

2) PostgreSQL with transactional guarantees
    - Centralized wallet tracking with ACID transactions.
    - Concurrency and consistency are ensured via transaction boundaries.

3) Clean Architecture/Layered approach
    - Clear separation between domain, application/use cases, delivery (HTTP), data (repositories), and infrastructure.

4) Testing strategy
    - Unit tests for use cases and domain logic.
    - Integration tests for repository and controller interactions.
    - E2E tests for full user flows via HTTP endpoints.

---

## Improvements and Not Implemented Features

- Authentication/Authorization (e.g., JWT). The current implementation assumes trusted clients.
- Pagination for transaction history.
- Advanced user lifecycle management (e.g., updates, bans, expirations).
- Rich error handling and error codes.
- Structured logging, tracing, and observability.
- Security hardening (validation, rate limiting, etc.).
- Caching for performance hot paths.
- CI/CD pipelines and deployment templates.
- Multi-currency support and currency conversion.

---

## Project Structure

A high-level view of the code organization following a layered/clean architecture:
[Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)

```text
├── application                     # Use cases and services
├── domain                          # Core business models
├── data                            # Data access, repositories, DTOs
├── infrastructure                  # Infrastructure: DB configs (empty for now)
├── delivery                        # HTTP controllers and mappers
├── FirstcircleTestApplication.kt   # Main Spring Boot application
```