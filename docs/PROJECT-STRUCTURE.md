# Структура репозитория

```text
.
├── pom.xml                          Maven, Spring Boot 3.5, Java 21
├── mvnw, mvnw.cmd, .mvn/            Maven Wrapper — IDEA и CI без системного Maven
├── compose.yaml                     api + PostgreSQL
├── Dockerfile                       multi-stage: build → JRE 21
├── README.md
├── docs/                            карта развития и каноны
│   ├── README.md
│   ├── OPEN-QUESTIONS.md
│   ├── DOMAIN.md
│   ├── ARCHITECTURE.md
│   ├── ROADMAP.md
│   ├── PROJECT-STRUCTURE.md
│   ├── LOCAL-DEVELOPMENT.md
│   ├── DEPLOYMENT.md
│   └── adr/
├── http/api.http                    запросы для IDEA HTTP Client
├── web/                             Vite + React UI
├── .run/                            run configuration для IDEA
└── src
    ├── main/java/com/ledger
    │   ├── LedgerApiApplication.java
    │   ├── api/                     HTTP
    │   ├── application/             сценарии
    │   ├── domain/                  модель
    │   └── infrastructure/
    │       ├── config/
    │       └── persistence/
    ├── main/resources
    │   ├── application.yml
    │   └── db/migration/            Flyway
    └── test/                        интеграционные тесты API на H2
```

Монолитный модуль `ledger-api`. Второй Maven-модуль (`web`, `domain`) заводим только когда появится вторая собираемая штука, а не заранее.

Имена таблиц в единственном числе (`stock_portfolio`, `expense_entry`): проще маппить на сущность.
