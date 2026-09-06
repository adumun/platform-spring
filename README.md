# ADÜMÜN Platform Spring

Reusable Spring implementation modules for ADÜMÜN platform standards.

## Current slice

`SPRING-BASE-001` materializes two modules only:

- `adumun-spring-rest` — Spring MVC integration for request/correlation context, shared envelopes, centralized error handling and liveness/readiness semantics.
- `adumun-spring-test` — reusable Spring testing support for PostgreSQL Testcontainers and Flyway migration verification.

`adumun-spring-security` and observability are intentionally outside this slice.

## Runtime baseline

- Java 25 LTS
- Spring Boot 4.1.1
- Maven

## Validation

```bash
make validate
```

Equivalent command:

```bash
mvn -B clean verify
```

The reference consumer under `examples/reference-consumer` proves the modules can be wired without GradeOps, KeyGo or other product-domain assumptions.
