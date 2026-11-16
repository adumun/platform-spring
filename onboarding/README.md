# Isolated onboarding projects

These projects validate `platform-spring` from the consumer side.

They are intentionally **not Maven modules of the root `platform-spring` reactor**. Each directory is a standalone Spring Boot build with its own `pom.xml`, a normal Spring Boot parent, and only public ADÜMÜN artifact coordinates.

They MUST NOT use relative parents, source-directory dependencies, manual imports of ADÜMÜN infrastructure configuration, or unpublished internal test fixtures.

## Cases

| ID | Project | Primary consumer path |
|---|---|---|
| `SPRING-ONB-REST-001` | `rest-minimal` | REST auto-configuration and request/correlation propagation |
| `SPRING-ONB-SEC-CUSTOM-001` | `security-custom-decoder` | Security core with a consumer-owned `JwtDecoder` and authorization rule |
| `SPRING-ONB-SEC-OIDC-001` | `security-oidc` | Generic OIDC/JWT adapter configured declaratively |
| `SPRING-ONB-OBS-001` | `observability-minimal` | Operational identity and observability auto-configuration |
| `SPRING-ONB-RES-001` | `resilience-dependency` | Named dependency resilience policy materialization |
| `SPRING-ONB-FULL-001` | `full-stack-service` | REST + Security/OIDC + Observability + Resilience composition |

## Local candidate-artifact mode

```bash
make onboarding
```

The harness uses `.validation/onboarding-m2` rather than the developer's normal `~/.m2`. It removes `io/adumun/platform` before each run, installs the exact candidate artifacts from the implementation reactor, and then executes every onboarding project as a separate Maven build.

Third-party dependencies remain cached in that isolated repository so repeated onboarding validation does not continuously download the full Spring ecosystem.

This produces **consumer-boundary local-artifact evidence**. It proves that consumers resolve compiled ADÜMÜN artifacts through a Maven repository boundary; it does not prove remote publication.

## Published-artifact mode

```bash
make onboarding PUBLISHED=1 VERSION=<published-version>
```

Published mode does not install ADÜMÜN artifacts from the current checkout. It is intended for release/distribution verification once an ADÜMÜN Maven repository is configured for consumers.

## Diagnostics

Default output is concise and case-oriented. Logs are written to:

```text
.validation/onboarding/
```

For complete Maven/Spring output:

```bash
make onboarding VERBOSE=1
```

## Evolution rule

A materially new provider, protocol, adapter or adoption path must add or update an isolated onboarding case. Firebase and Microsoft Entra cases will be added when those adapters are materialized.
