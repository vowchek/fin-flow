package com.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "price_history")
public class PriceHistory implements Persistable<UUID> {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssetMarket market;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "price_date", nullable = false)
    private LocalDate priceDate;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal price;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected PriceHistory() {
    }

    public PriceHistory(
            UUID id,
            AssetMarket market,
            UUID instrumentId,
            LocalDate priceDate,
            BigDecimal price,
            String currency
    ) {
        this.id = id;
        this.market = market;
        this.instrumentId = instrumentId;
        this.priceDate = priceDate;
        this.price = price;
        this.currency = currency;
    }

    @PrePersist
    void onCreate() {
        if (fetchedAt == null) {
            fetchedAt = Instant.now();
        }
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fetchedAt == null;
    }

    public AssetMarket getMarket() {
        return market;
    }

    public UUID getInstrumentId() {
        return instrumentId;
    }

    public LocalDate getPriceDate() {
        return priceDate;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }
}
