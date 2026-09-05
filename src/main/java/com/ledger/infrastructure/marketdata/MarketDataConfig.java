package com.ledger.infrastructure.marketdata;

import com.ledger.domain.AssetMarket;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Configuration
@EnableConfigurationProperties(MarketDataProperties.class)
public class MarketDataConfig {

    @Bean(name = "moexRestClient")
    @Profile("!test")
    RestClient moexRestClient(MarketDataProperties properties) {
        return restClient(properties.getMoex().getBaseUrl());
    }

    @Bean(name = "coinGeckoRestClient")
    @Profile("!test")
    RestClient coinGeckoRestClient(MarketDataProperties properties) {
        return restClient(properties.getCoingecko().getBaseUrl());
    }

    @Bean(name = "yahooRestClient")
    @Profile("!test")
    RestClient yahooRestClient() {
        return restClient("https://query1.finance.yahoo.com");
    }

    @Bean(name = "tinvestRestClient")
    @Profile("!test")
    RestClient tinvestRestClient(MarketDataProperties properties) throws Exception {
        var tinvest = properties.getTinvest();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                        .sslContext(RussianTrustedSsl.create())
                        .connectTimeout(Duration.ofSeconds(10))
                        .build()
        );
        factory.setReadTimeout(Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(tinvest.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader("User-Agent", "fin-flow/0.1")
                .defaultHeader("x-app-name", "fin-flow");
        if (tinvest.isConfigured()) {
            builder.defaultHeader("Authorization", "Bearer " + tinvest.getToken().trim());
        }
        return builder.build();
    }

    @Bean
    @Profile("test")
    MarketDataProvider stubMoexProvider() {
        return new StubMarketDataProvider(AssetMarket.MOEX, "RUB");
    }

    @Bean
    @Profile("test")
    MarketDataProvider stubCryptoProvider() {
        return new StubMarketDataProvider(AssetMarket.CRYPTO, "USD");
    }

    private static RestClient restClient(String baseUrl) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader("User-Agent", "fin-flow/0.1")
                .build();
    }

    static final class StubMarketDataProvider implements MarketDataProvider {
        private final AssetMarket market;
        private final String currency;

        StubMarketDataProvider(AssetMarket market, String currency) {
            this.market = market;
            this.currency = currency;
        }

        @Override
        public AssetMarket market() {
            return market;
        }

        @Override
        public List<RemoteInstrument> search(String query) {
            String q = query.trim().toUpperCase(Locale.ROOT);
            String name = market == AssetMarket.CRYPTO ? "Stub Coin " + q : "Stub Share " + q;
            String external = market == AssetMarket.CRYPTO ? q.toLowerCase(Locale.ROOT) : q;
            return List.of(new RemoteInstrument(q, external, name, null, currency));
        }

        @Override
        public Optional<InstrumentProfile> fetchInstrumentProfile(String externalId) {
            String id = externalId.trim();
            String symbol = market == AssetMarket.CRYPTO ? id.toUpperCase(Locale.ROOT) : id.toUpperCase(Locale.ROOT);
            String external = market == AssetMarket.CRYPTO ? id.toLowerCase(Locale.ROOT) : id.toUpperCase(Locale.ROOT);
            String name = market == AssetMarket.CRYPTO ? "Stub Coin " + symbol : "Stub Share " + symbol;
            return Optional.of(new InstrumentProfile(symbol, external, name, currency, "EQUITY"));
        }

        @Override
        public List<CorporatePayment> fetchCorporatePayments(String externalId) {
            if (market != AssetMarket.MOEX) {
                return List.of();
            }
            LocalDate today = LocalDate.now();
            return List.of(
                    new CorporatePayment(today.minusMonths(6), new BigDecimal("12.50"), currency, "DIVIDEND"),
                    new CorporatePayment(today.minusMonths(18), new BigDecimal("10.00"), currency, "DIVIDEND")
            );
        }

        @Override
        public Optional<RemoteQuote> fetchLive(String externalId) {
            return Optional.of(new RemoteQuote(
                    new BigDecimal("100.00"),
                    new BigDecimal("95.00"),
                    currency
            ));
        }

        @Override
        public Optional<BigDecimal> fetchHistorical(String externalId, LocalDate date) {
            return Optional.of(new BigDecimal("90.00"));
        }

        @Override
        public List<HistoricalPrice> fetchHistoricalRange(String externalId, LocalDate from, LocalDate to) {
            if (from == null || to == null || from.isAfter(to)) {
                return List.of();
            }
            List<HistoricalPrice> out = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                out.add(new HistoricalPrice(d, new BigDecimal("90.00")));
            }
            return out;
        }
    }
}
