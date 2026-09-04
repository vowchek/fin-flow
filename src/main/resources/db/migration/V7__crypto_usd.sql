UPDATE crypto_instrument SET currency = 'USD' WHERE currency <> 'USD';

DELETE FROM price_quote WHERE market = 'CRYPTO';
DELETE FROM price_history WHERE market = 'CRYPTO';
