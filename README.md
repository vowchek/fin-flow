# Fin Flow

API и UI **фондовых и криптопортфелей** и **месячных трат**: Java backend + Vite/React.

## Что уже умеет API

- регистрация / вход (JWT Bearer), роль USER/ADMIN
- каталог активов (отдельно фонд и крипта) с логотипами — админка
- фондовые портфели (Мосбиржа / ISS) и криптопортфели (CoinGecko)
- журнал сделок BUY/SELL, оценка портфеля и P&L (кэш цен 10 мин)
- выбор актива из курируемого каталога
- планировщик трат по месяцам и справочник категорий
- данные пользователей изолированы

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

## UI (web)

```bash
cd web
npm install
npm run dev
```

Откроется Vite на http://127.0.0.1:5173 (прокси на API `:48121`). Нужен запущенный backend.

## Docker

```bash
docker compose up --build
```

Поднимаются PostgreSQL 16 и API. Данные Postgres живут в volume `ledger_pg`.

Профиль `docker` читает БД из сервиса `postgres`. С хоста Postgres проброшен на порт **54332**.

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
