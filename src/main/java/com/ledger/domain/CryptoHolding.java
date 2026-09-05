package com.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "crypto_holding")
public class CryptoHolding implements Persistable<UUID> {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private CryptoPortfolio portfolio;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(length = 120)
    private String name;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal quantity;

    @Column(nullable = false)
    private boolean cash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CryptoHolding() {
    }

    public CryptoHolding(UUID id, CryptoPortfolio portfolio, String symbol, String name, BigDecimal quantity) {
        this(id, portfolio, symbol, name, quantity, false);
    }

    public CryptoHolding(
            UUID id,
            CryptoPortfolio portfolio,
            String symbol,
            String name,
            BigDecimal quantity,
            boolean cash
    ) {
        this.id = id;
        this.portfolio = portfolio;
        this.symbol = symbol;
        this.name = name;
        this.quantity = quantity;
        this.cash = cash;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public CryptoPortfolio getPortfolio() {
        return portfolio;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public boolean isCash() {
        return cash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean isNew() {
        return createdAt == null;
    }
}
