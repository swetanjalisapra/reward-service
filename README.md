# Rewards Service

Spring Boot REST service that calculates customer reward points for a 3‑month
window of transactions.

## Reward Rules
- 2 points per dollar spent **over $100** in a transaction.
- 1 point per dollar spent **between $50 and $100** in a transaction.
- Example: `$120` → `2 × 20 + 1 × 50 = 90 points`.

## Tech Stack
- Java 17, Spring Boot 3.3, Spring Web, Spring Data JPA
- H2 in-memory database (seed data in [src/main/resources/import.sql](src/main/resources/import.sql))
- Maven, Lombok, springdoc-openapi
- Docker (multi-stage build)

## Project Layout
```
src/main/java/com/retailer/rewards
  ├── RewardsServiceApplication.java
  ├── controller/   REST endpoints
  ├── service/      Business logic + pure calculator
  ├── repository/   Spring Data JPA repos
  ├── entity/      JPA entities
  ├── dto/          API response records
  └── exception/    Custom exceptions + @RestControllerAdvice
src/main/resources
  ├── application.yml
  └── import.sql    Seed dataset (3-month window: 2024-01..2024-03)
src/test/java       Unit + MockMvc integration tests
Dockerfile
pom.xml
```

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

## REST API

| Method | Path                                  | Description                                        |
|-------:|---------------------------------------|----------------------------------------------------|
| GET    | `/api/v1/rewards`                     | Rewards for all customers (defaults to last 3 mo). |
| GET    | `/api/v1/rewards/{customerId}`        | Rewards for a single customer.                     |

Optional query params on both endpoints: `start=YYYY-MM-DD`, `end=YYYY-MM-DD`.

### Examples
```bash
curl "http://localhost:8080/api/v1/rewards?start=2024-01-01&end=2024-03-31"
curl "http://localhost:8080/api/v1/rewards/1?start=2024-01-01&end=2024-03-31"
```

### Sample response
```json
[
  {
    "customerId": 1,
    "customerName": "Alice Johnson",
    "monthlyPoints": { "2024-01": 115, "2024-02": 250, "2024-03": 50 },
    "totalPoints": 415
  }
]
```

## Useful URLs
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- H2 console: `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:rewards`)
- Health:     `http://localhost:8080/actuator/health`

## Tests
```bash
mvn test
```

## Production Notes
- Swap H2 for Postgres/MySQL by changing `spring.datasource.*` and adding the
  driver dependency. Disable `spring.sql.init` and use Flyway/Liquibase for
  schema migrations.
- Disable `h2-console` and `spring.jpa.hibernate.ddl-auto` in production
  profiles (`application-prod.yml`).
- Add Spring Security for authentication and tighten actuator exposure.
- Container image runs as non-root user `app`.
