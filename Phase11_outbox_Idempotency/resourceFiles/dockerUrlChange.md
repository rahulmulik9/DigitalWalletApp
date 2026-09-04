# Docker Commands & Notes

## Build Image from Service

```bash
mvn clean compile jib:buildTar
docker load -i target/jib-image.tar
```

## Run Docker Compose

```bash
docker compose up -d
```

## Stop and Remove Containers

```bash
docker compose down -v
```

## Important Note: URLs When Running in Docker

When running services inside Docker, **`localhost` refers to the current container**, not another service.

Therefore, use the **Docker Compose service name** instead of `localhost`.

### PostgreSQL

When running locally:

```yaml
url: jdbc:postgresql://localhost:5434/TransactionService
```

When running inside Docker:

```yaml
url: jdbc:postgresql://transaction-db:5432/TransactionService
```

Here, `transaction-db` is the PostgreSQL service name defined in `docker-compose.yml`.

### Account Service

When running locally:

```yaml
account-service:
  url: http://localhost:8081
```

When running inside Docker:

```yaml
account-service:
  url: http://account-service:8081
```

Here, `account-service` is the Docker Compose service name.

### Rule to Remember

**Local machine → `localhost`**

**Docker → Docker Compose service name**

For example:

```text
localhost:5434
        ↓
transaction-db:5432

localhost:8081
        ↓
account-service:8081
```