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
Dockerfile
pom.xml
```

## Seed Data
On startup, `DataSeeder` populates H2 with **20 customers** and randomized
transactions spanning **2024-11-01 → 2026-05-06**. The data is deterministic
(fixed RNG seed) so results are reproducible across runs. The seeder is skipped
when the `prod` or `test` profile is active, or when data already exists.

## Run

```bash
./mvnw spring-boot:run        # or: mvn spring-boot:run
```

Service starts on `http://localhost:8080`.

### Docker
```bash
docker build -t rewards-service:1.0.0 .
docker run --rm -p 8080:8080 rewards-service:1.0.0
```
The image is built in two stages (Maven 3.9 / Temurin 17 → Temurin 17 JRE) and
runs as the non-root user `app`. Pass JVM flags via `JAVA_OPTS`, e.g.:
```bash
docker run --rm -p 8080:8080 -e JAVA_OPTS="-Xmx256m" rewards-service:1.0.0
```

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
