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
- `adumun-spring-security-oidc-client` — opt-in confidential OIDC login client (PKCE S256, constrained registration and consumer admission); explicit security-chain composition.
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

### OIDC Login Client — consumer-owned policy (ADU-181)

The login module is intentionally separate from JWT Resource Server. It does **not** create a `SecurityFilterChain`, override CSRF/session defaults, choose success redirects or alter other applications. The consuming product must supply an `AdumunOidcIdentityAcceptance` policy and compose its own ordered login and deny-by-default chains, using `AdumunOidcLoginConfigurer`.

Configure a confidential `authorization_code` registration in `spring.security.oauth2.client.registration` and a trusted issuer/JWKS provider in `spring.security.oauth2.client.provider`. Configure client credentials via secrets, never Git.

```yaml
adumun:
  security:
    oidc-client:
      enabled: true
      registration-id: external
      allowed-scopes: [openid]
```

The module enforces exact approved scopes (default `openid`) and PKCE S256 even for confidential clients. Provider issuer, authorization, token and JWKS URLs must use HTTPS; `adumun.security.oidc-client.allow-insecure-loopback=true` is available **only** for an explicit loopback-only simulated IdP in local tests and must never be used in production. Its default is false. The default OIDC user service does not call UserInfo. Spring Security handles state, nonce, token checks and code exchange. Identity's own hook resolves JIT identities and must deny revoked/suspended users. See `onboarding/security-oidc-login` for an isolated consumer example.

**Delivery gate:** ADU-181 remains a draft until full negative callback tests, JDK 25 validation, session/token retention, CSRF and real Identity adoption are evidenced. No local module test alone proves E2E.

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

### Implementation reactor

Default implementation validation is intentionally concise and reports real reactor progress:

```bash
make validate
```

Full underlying Maven/Spring/Testcontainers diagnostics are available with:

```bash
make validate VERBOSE=1
```

Successful compact validation writes the complete log to `.validation/validate.log`.

The reference consumer under `examples/reference-consumer` is the in-repository executable integration contract. It proves that shared modules compose without product-domain assumptions, but it is not by itself sufficient evidence of external adoption.

### Isolated consumer onboarding

Consumer-boundary validation runs standalone Spring Boot projects that are deliberately outside the root Maven reactor:

```bash
make onboarding
```

The harness installs the exact candidate ADÜMÜN artifacts into an isolated Maven repository under `.validation/onboarding-m2`, then starts a new Maven build for each onboarding project. The onboarding projects do not inherit the `platform-spring` parent, do not compile against source directories and do not manually import ADÜMÜN infrastructure configuration.

Current onboarding paths:

- REST minimal;
- Security with consumer-owned custom `JwtDecoder`;
- Generic OIDC/JWT Security;
- OIDC Login Client with consumer-owned chains;
- Observability minimal;
- named dependency Resilience;
- full-stack REST + Security/OIDC + Observability + Resilience composition.

Logs are written per case under `.validation/onboarding/`. Full output is available with:

```bash
make onboarding VERBOSE=1
```

Once ADÜMÜN Maven artifacts are remotely published, the same standalone projects support the stronger distribution gate:

```bash
make onboarding PUBLISHED=1 VERSION=<published-version>
```

See `onboarding/README.md` for the isolation contract and case matrix.
