package com.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_instrument")
public class StockInstrument implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, length = 32, unique = true)
    private String symbol;

    @Column(name = "external_id", nullable = false, length = 120, unique = true)
    private String externalId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "logo_filename", length = 120)
    private String logoFilename;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_kind", nullable = false, length = 16)
    private AssetKind assetKind = AssetKind.EQUITY;

    @Column(name = "annual_cashflow_per_unit", precision = 28, scale = 8)
    private BigDecimal annualCashflowPerUnit;

    @Column(name = "cashflow_growth_pct", precision = 12, scale = 6)
    private BigDecimal cashflowGrowthPct;

    @Column(name = "cashflow_until_year")
    private Integer cashflowUntilYear;

    @Column(name = "pays_dividends", nullable = false)
    private boolean paysDividends = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockInstrument() {
    }

    public StockInstrument(UUID id, String symbol, String externalId, String name, String currency) {
        this.id = id;
        this.symbol = symbol;
        this.externalId = externalId;
        this.name = name;
        this.currency = currency;
        this.enabled = true;
        this.assetKind = AssetKind.EQUITY;
        this.paysDividends = true;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (assetKind == null) {
            assetKind = AssetKind.EQUITY;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return createdAt == null;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLogoFilename() {
        return logoFilename;
    }

    public void setLogoFilename(String logoFilename) {
        this.logoFilename = logoFilename;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public AssetKind getAssetKind() {
        return assetKind == null ? AssetKind.EQUITY : assetKind;
    }

    public void setAssetKind(AssetKind assetKind) {
        this.assetKind = assetKind == null ? AssetKind.EQUITY : assetKind;
    }

    public BigDecimal getAnnualCashflowPerUnit() {
        return annualCashflowPerUnit;
    }

    public void setAnnualCashflowPerUnit(BigDecimal annualCashflowPerUnit) {
        this.annualCashflowPerUnit = annualCashflowPerUnit;
    }

    public BigDecimal getCashflowGrowthPct() {
        return cashflowGrowthPct;
    }

    public void setCashflowGrowthPct(BigDecimal cashflowGrowthPct) {
        this.cashflowGrowthPct = cashflowGrowthPct;
    }

    public Integer getCashflowUntilYear() {
        return cashflowUntilYear;
    }

    public void setCashflowUntilYear(Integer cashflowUntilYear) {
        this.cashflowUntilYear = cashflowUntilYear;
    }

    public boolean isPaysDividends() {
        return paysDividends;
    }

    public void setPaysDividends(boolean paysDividends) {
        this.paysDividends = paysDividends;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
