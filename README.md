# Rewards Service

Spring Boot REST service that calculates customer reward points over a
configurable date window of transactions (defaults to the last 3 months).

## Reward Rules
- 2 points per dollar spent **over $100** in a transaction.
- 1 point per dollar spent **between $50 and $100** in a transaction.
- Example: `$120` → `2 × 20 + 1 × 50 = 90 points`.

## Tech Stack
- Java 17, Spring Boot 3.3.4
- Spring Web, Spring Data JPA, Spring Validation, Spring Boot Actuator
- H2 in-memory database (seeded at startup by [DataSeeder](src/main/java/com/retailer/rewards/config/DataSeeder.java))
- Maven, Lombok, springdoc-openapi 2.6
- Docker (multi-stage build, runs as non-root)

## Project Layout
```
src/main/java/com/retailer/rewards
  ├── RewardsServiceApplication.java
  ├── config/       DataSeeder (seeds H2 on startup; disabled in prod/test)
  ├── controller/   REST endpoints
  ├── service/      Business logic (RewardsService, DateRange)
  ├── repository/   Spring Data JPA repos + projection (CustomerMonthlyPoints)
  ├── entity/       JPA entities (Customer, Transaction)
  ├── dto/          API response records
  └── exception/    Custom exceptions + @RestControllerAdvice
src/main/resources
  ├── application.yml
  └── logback-spring.xml
src/test/java/com/retailer/rewards
  ├── RewardsServiceApplicationTests.java   Context smoke test
  ├── controller/RewardsControllerTest.java   @WebMvcTest + MockMvc
  ├── service/RewardsServiceTest.java         Mockito unit tests
  ├── exception/GlobalExceptionHandlerTest.java
  └── repository/TransactionRepositoryTest.java   @DataJpaTest against H2
src/test/resources
  └── application-test.yml
Dockerfile
pom.xml
start.sh         Launch the service (Docker or Maven)
run-demo.sh      Build, run, hit the API, save responses to ./examples/
```

## Seed Data
On startup, `DataSeeder` populates H2 with **20 customers** and randomized
transactions spanning **2024-11-01 → 2026-05-06**. The data is deterministic
(fixed RNG seed) so results are reproducible across runs. The seeder is skipped
when the `prod` or `test` profile is active, or when data already exists.

## Run

The quickest path is the bundled launcher:

```bash
./start.sh           # auto: Docker if the daemon is running, else Maven
./start.sh docker    # force Docker (build image + run container)
./start.sh mvn       # force Maven (./mvnw spring-boot:run)
```

Overrides: `PORT`, `IMAGE`, `CONTAINER`. Service listens on
`http://localhost:8080` by default.

### Manual alternatives
```bash
./mvnw spring-boot:run
# or
docker build -t rewards-service:1.0.0 .
docker run --rm -p 8080:8080 rewards-service:1.0.0
```
The image is built in two stages (Maven 3.9 / Temurin 17 → Temurin 17 JRE) and
runs as the non-root user `app`. Pass JVM flags via `JAVA_OPTS`, e.g.:
```bash
docker run --rm -p 8080:8080 -e JAVA_OPTS="-Xmx256m" rewards-service:1.0.0
```

### Demo script
[run-demo.sh](run-demo.sh) builds the image, starts a container, waits for
`/actuator/health`, exercises every endpoint, and writes the responses (plus a
Markdown summary) into `./examples/`:
```bash
./run-demo.sh                  # cleans up the container on exit
KEEP_RUNNING=1 ./run-demo.sh   # leave the container up afterwards
```
Requires `docker` and `curl`; uses `jq` for pretty JSON if available.

## REST API

| Method | Path                                  | Description                                        |
|-------:|---------------------------------------|----------------------------------------------------|
| GET    | `/api/v1/rewards`                     | Rewards for all customers (defaults to last 3 mo). |
| GET    | `/api/v1/rewards/{customerId}`        | Rewards for a single customer.                     |

Optional query params on both endpoints: `start=YYYY-MM-DD`, `end=YYYY-MM-DD`.
Both must be supplied together; otherwise the default last-3-months window is
used. `end` must not be before `start`. Unknown `customerId` returns `404`
(`CustomerNotFoundException`).

### Examples
```bash
curl "http://localhost:8080/api/v1/rewards?start=2026-02-01&end=2026-04-30"
curl "http://localhost:8080/api/v1/rewards/1?start=2026-02-01&end=2026-04-30"
```

### Sample response
```json
[
  {
    "customerId": 1,
    "customerName": "Alice Johnson",
    "monthlyPoints": { "2026-02": 115, "2026-03": 250, "2026-04": 50 },
    "totalPoints": 415
  }
]
```

## Useful URLs
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI:    `http://localhost:8080/v3/api-docs`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:rewards`, user `sa`, empty password)
- Health:     `http://localhost:8080/actuator/health`
- Metrics:    `http://localhost:8080/actuator/metrics`

## Configuration
Key settings in [application.yml](src/main/resources/application.yml):
- `spring.datasource.url`: H2 in-memory (`jdbc:h2:mem:rewards`)
- `spring.jpa.hibernate.ddl-auto`: `create-drop`
- `management.endpoints.web.exposure.include`: `health,info,metrics`

### Tunable application properties
All values below are overridable via environment variables (or any standard
Spring property source).

| Property | Env var | Default | Purpose |
|---|---|---|---|
| `rewards.default-window-months` | `REWARDS_DEFAULT_WINDOW_MONTHS` | `3` | Months back used when `start`/`end` are omitted. |
| `seeder.start-date` | `SEEDER_START_DATE` | `2024-11-01` | First date of seeded transactions. |
| `seeder.end-date` | `SEEDER_END_DATE` | `2026-05-06` | Last date of seeded transactions. |
| `seeder.random-seed` | `SEEDER_RANDOM_SEED` | `42` | RNG seed (deterministic data). |
| `seeder.min-transactions-per-customer` | `SEEDER_MIN_TX` | `15` | Lower bound of transactions per customer. |
| `seeder.max-transactions-per-customer` | `SEEDER_MAX_TX` | `40` | Upper bound of transactions per customer. |

Example (Docker):
```bash
docker run --rm -p 8080:8080 \
  -e REWARDS_DEFAULT_WINDOW_MONTHS=6 \
  -e SEEDER_RANDOM_SEED=123 \
  rewards-service:1.0.0
```

## Tests

The project ships with **29 unit/integration tests** covering the service,
controller, exception handler, and repository layers (JUnit 5, Mockito,
AssertJ, Spring `MockMvc`, `@DataJpaTest` against H2). The `DataSeeder` is
disabled under the `test` profile.

| Suite | Type | Coverage |
|---|---|---|
| `RewardsServiceTest` | Mockito unit | Grouping, totals, default window, null/zero points, date-range validation, customer-not-found, empty results. |
| `RewardsControllerTest` | `@WebMvcTest` + MockMvc | Endpoint happy paths, query-param binding, 404/400 mapping, malformed date, non-numeric customerId. |
| `GlobalExceptionHandlerTest` | Plain unit | 404, 400, friendly `LocalDate` type-mismatch message, generic mismatch, 500 fallback, null-message handling. |
| `TransactionRepositoryTest` | `@DataJpaTest` (H2) | JPQL aggregation across customers/months, range exclusion, per-customer filter, empty result. |
| `RewardsServiceApplicationTests` | `@SpringBootTest` | Application context loads. |

### Run with Maven
```bash
./mvnw test
# or
mvn test
```

### Run with Docker (no local Maven required)
```bash
docker run --rm \
  -v "$PWD":/workspace -v "$HOME/.m2":/root/.m2 \
  -w /workspace maven:3.9-eclipse-temurin-17 \
  mvn test
```

Test reports are written to `target/surefire-reports/`.

