package com.ledger.infrastructure.marketdata;

import com.ledger.domain.AssetMarket;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ledger.market-data")
public class MarketDataProperties {

    private Duration quoteTtl = Duration.ofMinutes(10);
    private String quoteCurrency = "RUB";
    private String cryptoQuoteCurrency = "USD";
    private final Moex moex = new Moex();
    private final CoinGecko coingecko = new CoinGecko();

    public Duration getQuoteTtl() {
        return quoteTtl;
    }

    public void setQuoteTtl(Duration quoteTtl) {
        this.quoteTtl = quoteTtl;
    }

    public String getQuoteCurrency() {
        return quoteCurrency;
    }

    public void setQuoteCurrency(String quoteCurrency) {
        this.quoteCurrency = quoteCurrency;
    }

    public String getCryptoQuoteCurrency() {
        return cryptoQuoteCurrency;
    }

    public void setCryptoQuoteCurrency(String cryptoQuoteCurrency) {
        this.cryptoQuoteCurrency = cryptoQuoteCurrency;
    }

    public String quoteCurrency(AssetMarket market) {
        return market == AssetMarket.CRYPTO ? cryptoQuoteCurrency : quoteCurrency;
    }

    public Moex getMoex() {
        return moex;
    }

    public CoinGecko getCoingecko() {
        return coingecko;
    }

    public static class Moex {
        private String baseUrl = "https://iss.moex.com";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class CoinGecko {
        private String baseUrl = "https://api.coingecko.com/api/v3";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public AssetMarket marketForPortfolioKind(String kind) {
        return "crypto".equalsIgnoreCase(kind) ? AssetMarket.CRYPTO : AssetMarket.MOEX;
    }
}
