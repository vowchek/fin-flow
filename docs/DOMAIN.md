# Предметная область

API учёта **фондовых и криптопортфелей** (с журналом сделок) и **месячных трат**. Пользователь аутентифицирован (JWT); все сущности привязаны к `owner_id`.

## Допущения

| Тема | Допущение |
| --- | --- |
| Пользователи | Email/password, JWT. Роль `USER` / `ADMIN` (админы через `ADMIN_EMAILS`). |
| Каталог | Отдельные таблицы `stock_instrument` и `crypto_instrument` (курирует админ). |
| Лого | Файл на диске + `logo_filename`; URL `/api/v1/media/.../logo`. |
| Цены | Backend ходит в ISS/CoinGecko по `external_id`; кэш 10 мин. |
| Выбор актива | Пользователь видит только enabled-каталог. |
| Фонд / крипта | MOEX и CoinGecko соответственно. |
| Сделка | BUY/SELL; `unit_price` из запроса или истории на дату. |

## Сущности

```text
AppUser — role USER|ADMIN
StockInstrument / CryptoInstrument — symbol, externalId, name, logo, currency, enabled
PriceQuote / PriceHistory — кэш по (market, instrumentId)
Portfolios + holdings + transactions
```
