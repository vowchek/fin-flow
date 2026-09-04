package com.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "price_quote")
public class PriceQuote implements Persistable<PriceQuote.QuoteId> {

    @EmbeddedId
    private QuoteId id;

    @Column(nullable = false, precision = 28, scale = 8)
    private BigDecimal price;

    @Column(name = "previous_close", precision = 28, scale = 8)
    private BigDecimal previousClose;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Transient
    private boolean newEntity = true;

    protected PriceQuote() {
    }

    public PriceQuote(AssetMarket market, UUID instrumentId, BigDecimal price, BigDecimal previousClose, String currency, Instant fetchedAt) {
        this.id = new QuoteId(market, instrumentId);
        this.price = price;
        this.previousClose = previousClose;
        this.currency = currency;
        this.fetchedAt = fetchedAt;
        this.newEntity = true;
    }

    @PostLoad
    void markLoaded() {
        this.newEntity = false;
    }

    @Override
    public QuoteId getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public AssetMarket getMarket() {
        return id.market;
    }

    public UUID getInstrumentId() {
        return id.instrumentId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getPreviousClose() {
        return previousClose;
    }

    public void setPreviousClose(BigDecimal previousClose) {
        this.previousClose = previousClose;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getFetchedAt() {
        return fetchedAt;
    }

    public void setFetchedAt(Instant fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    @Embeddable
    public static class QuoteId implements Serializable {
        @Enumerated(EnumType.STRING)
        @Column(name = "market", length = 16, nullable = false)
        private AssetMarket market;

        @Column(name = "instrument_id", nullable = false)
        private UUID instrumentId;

        protected QuoteId() {
        }

        public QuoteId(AssetMarket market, UUID instrumentId) {
            this.market = market;
            this.instrumentId = instrumentId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof QuoteId quoteId)) return false;
            return market == quoteId.market && Objects.equals(instrumentId, quoteId.instrumentId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(market, instrumentId);
        }
    }
}
