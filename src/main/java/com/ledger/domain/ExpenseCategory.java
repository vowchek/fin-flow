package com.ledger.domain;

public enum ExpenseCategory {
    HOUSING("Жильё / аренда"),
    UTILITIES("ЖКХ"),
    GROCERIES("Продукты"),
    TRANSPORT("Транспорт"),
    HEALTH("Здоровье"),
    ENTERTAINMENT("Развлечения"),
    SUBSCRIPTIONS("Подписки"),
    EDUCATION("Образование"),
    CLOTHING("Одежда"),
    COMMUNICATION("Связь"),
    CAFES("Кафе / рестораны"),
    OTHER("Прочее");

    private final String label;

    ExpenseCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
