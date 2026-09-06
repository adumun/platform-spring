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
- Maven Wrapper 3.3.4
- Apache Maven 3.9.11 (wrapper-managed)

A global Maven installation is not required. Repository validation must use the checked-in Maven Wrapper.

## Validation

```bash
make validate
```

Equivalent command:

```bash
./mvnw -B clean verify
```

Useful direct commands:

```bash
./mvnw -version
./mvnw -B test
```

The reference consumer under `examples/reference-consumer` proves the modules can be wired without GradeOps, KeyGo or other product-domain assumptions.
