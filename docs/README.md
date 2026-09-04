# Документация Ledger

Читать в таком порядке:

1. [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md) — вопросы, без ответов на которые план будет слишком общим.
2. [DOMAIN.md](DOMAIN.md) — что система учитывает **сейчас** и какие допущения приняты.
3. [ARCHITECTURE.md](ARCHITECTURE.md) — как устроен backend и как он будет меняться.
4. [PROJECT-STRUCTURE.md](PROJECT-STRUCTURE.md) — где лежит код.
5. [ROADMAP.md](ROADMAP.md) — этапы развития.
6. [LOCAL-DEVELOPMENT.md](LOCAL-DEVELOPMENT.md) — IDEA, Maven, профили Spring.
7. [DEPLOYMENT.md](DEPLOYMENT.md) — Docker → сервер → БД.
8. [adr/](adr/) — решения, которые уже зафиксированы.

Канон, которому следуем на старте:

- один репозиторий, один деплойный артефакт (API);
- схема БД только через Flyway, без `ddl-auto=update` в постоянных средах;
- контракт API версионируется (`/api/v1`);
- поведение фиксируется тестами, а не «ручным прокликиванием»;
- следующее усложнение (auth, UI, отдельный сервер) не тащится в код, пока не начат соответствующий этап.
