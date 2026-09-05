package com.ledger.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "stock_portfolio")
public class StockPortfolio {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_mode", nullable = false, length = 32)
    private PortfolioEntryMode entryMode = PortfolioEntryMode.MANUAL;

    @Column(name = "tax_rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal taxRatePercent = new BigDecimal("13");

    @OneToMany(mappedBy = "portfolio", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("symbol ASC")
    private List<StockHolding> holdings = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockPortfolio() {
    }

    public StockPortfolio(UUID id, AppUser owner, String name, String description, PortfolioEntryMode entryMode) {
        this.id = id;
        this.owner = owner;
        this.name = name;
        this.description = description;
        this.entryMode = entryMode == null ? PortfolioEntryMode.MANUAL : entryMode;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (entryMode == null) {
            entryMode = PortfolioEntryMode.MANUAL;
        }
        if (taxRatePercent == null) {
            taxRatePercent = new BigDecimal("13");
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PortfolioEntryMode getEntryMode() {
        return entryMode;
    }

    public void setEntryMode(PortfolioEntryMode entryMode) {
        this.entryMode = entryMode == null ? PortfolioEntryMode.MANUAL : entryMode;
    }

    public BigDecimal getTaxRatePercent() {
        return taxRatePercent == null ? new BigDecimal("13") : taxRatePercent;
    }

    public void setTaxRatePercent(BigDecimal taxRatePercent) {
        this.taxRatePercent = taxRatePercent == null ? new BigDecimal("13") : taxRatePercent;
    }

    public List<StockHolding> getHoldings() {
        return holdings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
