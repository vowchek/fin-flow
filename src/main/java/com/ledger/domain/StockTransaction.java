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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TxKind kind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_holding_id")
    private StockHolding relatedHolding;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "unit_price", precision = 28, scale = 8)
    private BigDecimal unitPrice;

    @Column(length = 1000)
    private String note;

    @Column(name = "settle_to_cash", nullable = false)
    private boolean settleToCash = true;

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
        this(id, holding, side, TxKind.TRADE, null, quantity, occurredOn, unitPrice, note);
    }

    public StockTransaction(
            UUID id,
            StockHolding holding,
            TradeSide side,
            TxKind kind,
            StockHolding relatedHolding,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note
    ) {
        this(id, holding, side, kind, relatedHolding, quantity, occurredOn, unitPrice, note, true);
    }

    public StockTransaction(
            UUID id,
            StockHolding holding,
            TradeSide side,
            TxKind kind,
            StockHolding relatedHolding,
            BigDecimal quantity,
            LocalDate occurredOn,
            BigDecimal unitPrice,
            String note,
            boolean settleToCash
    ) {
        this.id = id;
        this.holding = holding;
        this.side = side;
        this.kind = kind == null ? TxKind.TRADE : kind;
        this.relatedHolding = relatedHolding;
        this.quantity = quantity;
        this.occurredOn = occurredOn;
        this.unitPrice = unitPrice;
        this.note = note;
        this.settleToCash = settleToCash;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        if (kind == null) {
            kind = TxKind.TRADE;
        }
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

    public TxKind getKind() {
        return kind;
    }

    public StockHolding getRelatedHolding() {
        return relatedHolding;
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

    public boolean isSettleToCash() {
        return settleToCash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
