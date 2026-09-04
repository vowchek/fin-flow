# Ledger

Учёт **времени** и **денег**: Java API, который можно открыть в IntelliJ IDEA, запустить локально или в Docker, и затем вырастить до сервера с PostgreSQL и UI.

Сейчас это **этап 0**: только backend. UI в план заложен, кода фронтенда нет.

## Что уже умеет API

- проекты (группировка работ и платежей)
- записи времени (`startedAt` / `endedAt`, длительность считается сама)
- доходы и расходы
- сводка за период: минуты, приход, расход, net

Интерактивная документация: [Swagger UI](http://127.0.0.1:48121/swagger-ui.html) после запуска.

## Открыть в IntelliJ IDEA

1. `File → Open` и укажите корень репозитория (файл `pom.xml`).
2. Дождитесь импорта Maven (JDK **21**).
3. Запустите `com.ledger.LedgerApiApplication` или конфигурацию **LedgerApiApplication** из `.run/`.
4. Профиль по умолчанию — `local`: встроенный H2, файл `./data/ledger.mv.db`. Docker не нужен.

Порт: **48121**.

Примеры запросов: `http/api.http` (встроенный HTTP Client IDEA).

## Запуск из терминала

```bash
./mvnw spring-boot:run
```

Проверка:

```bash
curl http://127.0.0.1:48121/actuator/health
```

Тесты:

```bash
./mvnw test
```

## Docker (этап 1)

Когда Docker установлен:

```bash
docker compose up --build
```

Поднимаются PostgreSQL 16 и API. Данные Postgres живут в volume `ledger_pg`.

Профиль `docker` читает БД из сервиса `postgres`. С хоста Postgres проброшен на порт **54332**, чтобы не конфликтовать с локальным 5432.

## Куда смотреть дальше

| Документ | Содержание |
| --- | --- |
| [docs/README.md](docs/README.md) | Навигация по документации |
| [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) | Чего не хватает, чтобы уточнить план |
| [docs/ROADMAP.md](docs/ROADMAP.md) | Этапы от локального Docker до сервера |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Слои, стек, эволюция БД |
| [docs/DOMAIN.md](docs/DOMAIN.md) | Предметная модель |
| [docs/PROJECT-STRUCTURE.md](docs/PROJECT-STRUCTURE.md) | Дерево репозитория |
| [docs/LOCAL-DEVELOPMENT.md](docs/LOCAL-DEVELOPMENT.md) | IDEA, профили, миграции |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Как расти к продакшену |
