# ADÜMÜN Platform Spring

Reusable Spring implementation modules for ADÜMÜN platform standards.

## Runtime baseline

- Java 25 LTS
- Spring Boot 4.1.1
- Spring MVC / Servlet as the default web execution model
- Maven Wrapper 3.3.4
- Apache Maven 3.9.11 (wrapper-managed)

A global Maven installation is not required. Repository validation must use the checked-in Maven Wrapper.

## Modules

- `adumun-spring-rest` — request/correlation context, shared response/error envelopes, centralized error handling, liveness/readiness, Servlet integration.
- `adumun-spring-test` — PostgreSQL Testcontainers and Flyway verification support.
- `adumun-spring-security` — provider-neutral stateless bearer security, standard 401/403 handling, declarative CORS, public-path configuration and narrow authorization extension points.
- `adumun-spring-security-oidc` — generic OIDC/JWT identity adapter that supplies `JwtDecoder` mechanics from declarative ADÜMÜN configuration while preserving issuer/audience validation and consumer override semantics.
- `adumun-spring-observability` — operational identity, Micrometer Observation/Tracing integration, OpenTelemetry bridge and request/correlation logging context.
- `adumun-spring-resilience` — named dependency policies for retry, exponential backoff/jitter, circuit breaker, bulkhead and timeout using Resilience4j Boot 4.

## Consumer experience

The normal adoption model is convention over configuration:

1. add the required ADÜMÜN module dependency;
2. configure variable values under `adumun.*`;
3. provide only product-specific extension points when necessary.

Consumers SHOULD NOT import ADÜMÜN infrastructure configuration classes or recreate Spring framework plumbing for normal cases. REST, security, identity adapters, observability and resilience infrastructure are registered through Spring Boot auto-configuration.

A consumer application can therefore remain a normal Spring Boot application:

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Security example

Typical variable configuration:

```yaml
adumun:
  security:
    public-paths:
      - /live
      - /ready
    cors:
      enabled: false
```

Application-specific authorization remains a narrow extension point:

```java
@Bean
AdumunAuthorizationRules authorizationRules() {
    return authorize -> authorize
        .requestMatchers("/admin/**")
        .hasAuthority("SCOPE_admin");
}
```

### Generic OIDC/JWT identity adapter

Add `adumun-spring-security-oidc` and configure the provider rather than creating a `JwtDecoder` for the common case:

```yaml
adumun:
  security:
    identity:
      oidc:
        enabled: true
        issuer-uri: https://idp.example.com/issuer
        audiences:
          - orders-api
```

If the provider's JWK Set URI is known explicitly, it can be supplied while issuer validation remains enabled:

```yaml
adumun:
  security:
    identity:
      oidc:
        enabled: true
        issuer-uri: https://idp.example.com/issuer
        jwk-set-uri: https://idp.example.com/.well-known/jwks.json
        audiences:
          - orders-api
```

The adapter is disabled by default. When enabled, `issuer-uri` is required. Every configured audience must be present in the token `aud` claim. A consumer-provided `JwtDecoder` takes precedence, preserving an escape hatch for specialized providers or custom validation.

Provider-specific adapters such as Firebase and Microsoft Entra should build on this model without introducing provider semantics into `adumun-spring-security`.

### Observability example

```yaml
adumun:
  observability:
    service-name: orders
    environment: production
    build-version: ${BUILD_VERSION:unknown}
    tracing:
      export-enabled: false
      sampling-probability: 0.1
```

### Resilience example

```yaml
adumun:
  resilience:
    dependencies:
      catalog:
        idempotent: true
        retry:
          enabled: true
          max-attempts: 3
        circuit-breaker:
          enabled: true
```

Retry, circuit breaker, bulkhead, timeout and trace export remain disabled unless explicitly configured. Non-idempotent work cannot enable retry without an explicit idempotency declaration.

## Validation

Default validation is intentionally concise and reports real reactor progress:

```bash
make validate
```

Full underlying Maven/Spring/Testcontainers diagnostics are available with:

```bash
make validate VERBOSE=1
```

Successful compact validation writes the complete log to `.validation/validate.log`.

The reference consumer under `examples/reference-consumer` is the executable consumer-experience contract. It must prove that shared modules work without product-domain assumptions and without manual imports of ADÜMÜN infrastructure configuration.
