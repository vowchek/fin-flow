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
    }
}
