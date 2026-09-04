# Архитектура

## Сейчас (этап 0–1)

Один Spring Boot процесс:

```text
HTTP  →  api (controllers, DTO, errors)
           →  application (use cases)
                →  domain (JPA-сущности + инварианты)
                     →  infrastructure.persistence (Spring Data)
                          →  H2 (профиль local) или PostgreSQL (docker / prod)
```

Схема:

```text
клиент (curl / IDEA HTTP / Swagger)
        │
        ▼
   ledger-api :48121
        │
        ├── local:  файл H2  ./data/ledger.mv.db
        └── docker/prod: PostgreSQL
```

Миграции Flyway общие: SQL писался так, чтобы идти и в H2 (`MODE=PostgreSQL`), и в Postgres 16. Это позволяет гонять тесты без контейнера, а Docker сразу проверяет «боевой» движок.

## Слои

| Пакет | Ответственность |
| --- | --- |
| `com.ledger.api` | HTTP, валидация входа, RFC 7807 `ProblemDetail` |
| `com.ledger.api.dto` | Контракт JSON. Не отдаём JPA-сущности наружу |
| `com.ledger.application` | Сценарии: создать запись, сводка, резолв проекта |
| `com.ledger.domain` | Модель и инварианты |
| `com.ledger.infrastructure` | Репозитории, OpenAPI, будущие адаптеры |

На этапе 0 доменные классы = JPA `@Entity`. Это осознанный долг: отдельный persistence-модель появится, если домен раздуется (таймер, счета, мультивалютность с курсами).

## Профили Spring

| Профиль | Когда | БД |
| --- | --- | --- |
| `local` (default) | IDEA, `./mvnw spring-boot:run` | H2 file |
| `docker` | `docker compose up` | PostgreSQL в compose |
| `prod` | сервер | PostgreSQL, URL из env |

`ddl-auto=validate`: Hibernate не создаёт таблицы. Источник правды — `src/main/resources/db/migration`.

## API

Префикс `/api/v1`. Ломающие изменения — только `/api/v2` или согласованная миграция клиентов.

Документация: springdoc, `/swagger-ui.html`, OpenAPI JSON `/api/docs`.

Наблюдаемость: Actuator `health` / `info` (liveness/readiness включены для будущего оркестратора).

## Что появится позже, не ломая эту схему

```text
этап 2   UI (SPA)  →  тот же /api/v1  +  CORS  +  auth
этап 3   reverse proxy (Caddy/Nginx)  →  api + static
этап 4   backups, метрики, отдельные миграции под нагрузку
```

UI **не** кладётся внутрь `src/main/resources/static` как долгосрочный дом: для первого прототипа можно, для нормальной разработки — отдельный пакет/директория `web/` на Vite. Решение зафиксируем ADR, когда дойдём до UI.

## Осознанно нет

- микросервисы (время и деньги — один bounded context)
- Kafka, Redis, отдельный BFF
- Kubernetes на старте
