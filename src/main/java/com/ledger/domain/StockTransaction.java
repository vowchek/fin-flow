package com.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "stock_transaction")
public class StockTransaction {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holding_id", nullable = false)
    private StockHolding holding;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private TradeSide side;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "unit_price", precision = 28, scale = 8)
    private BigDecimal unitPrice;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockTransaction() {
    }

    public StockTransaction(
            UUID id,
            StockHolding holding,
            TradeSide side,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note
    ) {
        this.id = id;
        this.holding = holding;
        this.side = side;
        this.quantity = quantity;
        this.occurredOn = occurredOn;
        this.unitPrice = unitPrice;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public StockHolding getHolding() {
        return holding;
    }

    public TradeSide getSide() {
        return side;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
