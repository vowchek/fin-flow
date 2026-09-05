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
@Table(name = "stock_instrument_payment")
public class StockInstrumentPayment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "instrument_id", nullable = false)
    private StockInstrument instrument;

    @Column(name = "occurred_on", nullable = false)
    private LocalDate occurredOn;

    @Column(name = "amount_per_unit", nullable = false, precision = 28, scale = 8)
    private BigDecimal amountPerUnit;

    @Column(nullable = false, length = 8)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InstrumentPaymentKind kind;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockInstrumentPayment() {
    }

    public StockInstrumentPayment(
            UUID id,
            StockInstrument instrument,
            LocalDate occurredOn,
            BigDecimal amountPerUnit,
            String currency,
            InstrumentPaymentKind kind
    ) {
        this.id = id;
        this.instrument = instrument;
        this.occurredOn = occurredOn;
        this.amountPerUnit = amountPerUnit;
        this.currency = currency;
        this.kind = kind;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public StockInstrument getInstrument() {
        return instrument;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public BigDecimal getAmountPerUnit() {
        return amountPerUnit;
    }

    public String getCurrency() {
        return currency;
    }

    public InstrumentPaymentKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
