# fintech-api

A production-grade banking transfer API built as a comprehensive study project for the **Spring Certified Professional** certification. Every design decision maps directly to exam topics — from AOP-based idempotency to transactional propagation and dynamic JPA queries.

---

## Why this project exists

Most certification study material is theoretical. This project takes the opposite approach: every Spring concept is implemented as a solution to a real engineering problem, so the *why* is never disconnected from the *what*.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2 |
| Persistence | Spring Data JPA + H2 (dev) |
| Build | Maven |
| Testing | JUnit 5 · Mockito · AssertJ |

---

## Core Spring concepts covered

### Inversion of Control & Dependency Injection
Constructor injection throughout — no field injection. Every bean wired explicitly, making dependencies visible and testable.

### AOP (Aspect-Oriented Programming)
Two production-grade aspects:
- **`@Idempotent`** — custom annotation + `@Around` advice that intercepts transfer requests and returns the existing result if the idempotency key was already processed. Solves the network retry double-charge problem.
- **`LoggingAspect`** — execution time logging across all `@Service` beans via `@within` pointcut expression. Zero changes to business code.

### Transaction Management
- `@Transactional(readOnly = true)` as class-level default, overridden per method
- `Propagation.REQUIRES_NEW` in `TransferAuditService` — audit records survive rollback of the outer transaction
- Pessimistic locking (`SELECT FOR UPDATE`) on account balances during transfers
- Optimistic locking (`@Version`) on the `Account` entity

### Spring Data JPA
- **Specifications** — dynamic multi-filter search with `JpaSpecificationExecutor`. Solves the combinatorial explosion of derived query methods for optional filters.
- **Projections** — Interface-based (closed + SpEL), Class-based (DTO with constructor expression), and Dynamic (`<T>` generic method)
- **Pagination** — `Pageable` + `Page<T>` with sort support via query parameters

### Configuration
- `@ConfigurationProperties` with `@Validated` — business rules (transfer limits, allowed currencies) loaded from YAML and validated at startup. Application won't start with invalid config.
- Relaxed Binding — `max-amount` in YAML maps to `maxAmount` in Java automatically
- Profiles — `dev` seeds sample data via `CommandLineRunner`, `test` isolates test execution

### Exception Handling
- `@RestControllerAdvice` with RFC 7807 Problem Details
- Specific handlers for `MethodArgumentNotValidException`, `MissingRequestHeaderException`, `HttpMessageNotReadableException`

### Testing

| Type | Annotation | What's tested |
|---|---|---|
| Unit | `@ExtendWith(MockitoExtension)` | Service logic in isolation |
| Web slice | `@WebMvcTest` | Controller layer with `@MockBean` |
| JPA slice | `@DataJpaTest` | Repositories and Specifications |
| Integration (mock) | `@SpringBootTest` + `@AutoConfigureMockMvc` | Full stack with simulated HTTP |
| Integration (real) | `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` | Full stack with real HTTP server |

Parameterized tests (`@MethodSource`, `@NullAndEmptySource`, `@ValueSource`) cover all 8 optional-filter combinations with a single test method.

---

## Domain

A banking transfer API with two aggregates:

```
Account  ──────────────────────────────  Transfer
  id                                       id
  ownerName                                idempotencyKey
  accountNumber (unique)                   sourceAccountNumber
  balance                                  destinationAccountNumber
  version  ← optimistic lock              amount
  createdAt                                status (PENDING → COMPLETED/FAILED)
                                           failureReason
                                           createdAt / updatedAt
```

---

## Running locally

**Requirements:** Java 21, Maven 3.8+

```bash
git clone https://github.com/your-username/fintech-api
cd fintech-api
mvn spring-boot:run
```

The `dev` profile starts automatically and seeds three accounts (`ACC-001`, `ACC-002`, `ACC-003`).

| Endpoint | Description |
|---|---|
| `GET /actuator/health` | Health check |
| `GET /api/v1/accounts` | List all accounts |
| `GET /api/v1/accounts/search` | Search with optional filters + pagination |
| `POST /api/v1/accounts` | Create account |
| `POST /api/v1/transfers` | Execute transfer (requires `Idempotency-Key` header) |
| `GET /h2-console` | H2 in-memory database console |

H2 console JDBC URL: `jdbc:h2:mem:fintechdb` · User: `sa` · Password: *(empty)*

---

## Running the tests

```bash
# All tests
mvn test

# By module
mvn test -Dtest=AccountSpecificationsTest   # JPA slice — Specifications
mvn test -Dtest=AccountProjectionsTest      # JPA slice — Projections
mvn test -Dtest=AccountIntegrationTest      # Integration — MockMvc
mvn test -Dtest=TransferIntegrationTest     # Integration — real HTTP
mvn test -Dtest=IdempotencyBugTest          # Documents the idempotency bug
mvn test -Dtest=RequiresNewAuditTest        # Proves REQUIRES_NEW isolation
```

---

## Key engineering decisions

**Why AOP for idempotency instead of a service-layer check?**
Keeps the `TransferService` focused on business logic. The idempotency concern is cross-cutting — it belongs in an aspect, not scattered across every write operation.

**Why `REQUIRES_NEW` in a separate bean?**
Self-invocation bypasses the Spring proxy. Calling `this.auditFailure()` from within the same class would ignore `@Transactional` entirely. A separate bean forces the call through the proxy, making propagation work as expected.

**Why Specifications over multiple repository methods?**
3 optional filters = 8 method combinations. 4 filters = 16. Specifications compose at runtime — one `findAll(Specification, Pageable)` call handles every combination.

---

## Certification topics map

| Topic | Where to find it |
|---|---|
| `@SpringBootApplication` meta-annotation | `FintechApiApplication.java` |
| Bean scopes and lifecycle | `DataInitializerConfig.java` |
| AOP pointcut expressions | `IdempotencyAspect.java`, `LoggingAspect.java` |
| `@Around` vs other advices | `IdempotencyAspect.java` |
| Transaction propagation | `TransferService.java`, `TransferAuditService.java` |
| Optimistic vs Pessimistic locking | `Account.java`, `AccountRepository.java` |
| Derived query methods | `AccountRepository.java` |
| JPA Specifications | `AccountSpecifications.java` |
| Projections (all 3 types) | `AccountProjections.java`, `AccountRepository.java` |
| `@ConfigurationProperties` + Relaxed Binding | `TransferProperties.java`, `AppConfig.java` |
| `@ControllerAdvice` + Problem Details | `GlobalExceptionHandler.java` |
| Test slices vs full context | All test classes |
| `@ParameterizedTest` providers | `AccountSpecificationsTest.java` |

---

## License

MIT