-- Historical crypto fills were quoted in RUB; clear so cost basis is rebuilt from USD quotes.
UPDATE crypto_transaction SET unit_price = NULL WHERE unit_price IS NOT NULL;
