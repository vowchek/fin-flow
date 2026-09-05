-- Whether DIVIDEND/COUPON (and similar) credit the cash holding balance.
-- false = income still attributed to related asset, cash qty unchanged.

ALTER TABLE stock_transaction
    ADD COLUMN settle_to_cash BOOLEAN NOT NULL DEFAULT TRUE;
