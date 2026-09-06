-- create expense_category table
CREATE TABLE expense_category (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name            VARCHAR(100) NOT NULL,
    display_order   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_expense_category_owner ON expense_category (owner_id, display_order);

-- migrate existing enum categories into expense_category for each owner
INSERT INTO expense_category (id, owner_id, name, display_order, created_at, updated_at)
SELECT
    gen_random_uuid(),
    e.owner_id,
    m.label,
    m.ord,
    NOW(),
    NOW()
FROM (
    SELECT DISTINCT owner_id FROM expense_entry
) e
CROSS JOIN (VALUES
    (0, 'Жильё / аренда'),
    (1, 'ЖКХ'),
    (2, 'Продукты'),
    (3, 'Транспорт'),
    (4, 'Здоровье'),
    (5, 'Развлечения'),
    (6, 'Подписки'),
    (7, 'Образование'),
    (8, 'Одежда'),
    (9, 'Связь'),
    (10, 'Кафе / рестораны'),
    (11, 'Прочее')
) m(ord, label);

-- add category_id column
ALTER TABLE expense_entry ADD COLUMN category_id UUID;

-- populate category_id by joining on owner + enum label
UPDATE expense_entry ee
SET category_id = (
    SELECT ec.id FROM expense_category ec
    WHERE ec.owner_id = ee.owner_id
      AND ec.name = CASE ee.category
          WHEN 'HOUSING' THEN 'Жильё / аренда'
          WHEN 'UTILITIES' THEN 'ЖКХ'
          WHEN 'GROCERIES' THEN 'Продукты'
          WHEN 'TRANSPORT' THEN 'Транспорт'
          WHEN 'HEALTH' THEN 'Здоровье'
          WHEN 'ENTERTAINMENT' THEN 'Развлечения'
          WHEN 'SUBSCRIPTIONS' THEN 'Подписки'
          WHEN 'EDUCATION' THEN 'Образование'
          WHEN 'CLOTHING' THEN 'Одежда'
          WHEN 'COMMUNICATION' THEN 'Связь'
          WHEN 'CAFES' THEN 'Кафе / рестораны'
          WHEN 'OTHER' THEN 'Прочее'
      END
    LIMIT 1
);

-- make category_id not null
ALTER TABLE expense_entry ALTER COLUMN category_id SET NOT NULL;

-- add FK
ALTER TABLE expense_entry
    ADD CONSTRAINT fk_expense_entry_category
    FOREIGN KEY (category_id) REFERENCES expense_category (id) ON DELETE RESTRICT;

-- drop old category column
ALTER TABLE expense_entry DROP COLUMN category;
