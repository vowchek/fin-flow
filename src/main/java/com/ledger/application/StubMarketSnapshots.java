package com.ledger.application;

import com.ledger.api.dto.MarketStripItemResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@Profile("test")
public class StubMarketSnapshots implements MarketSnapshots {

    @Override
    public List<MarketStripItemResponse> stockStrip() {
        return List.of(
                new MarketStripItemResponse("imoex", "IMOEX", new BigDecimal("2800"), new BigDecimal("0.4"), "", "₽"),
                new MarketStripItemResponse("usdrub", "USD/RUB", new BigDecimal("90"), new BigDecimal("-0.2"), "", "₽")
        );
    }

    @Override
    public List<MarketStripItemResponse> cryptoStrip() {
        return List.of(
                new MarketStripItemResponse("btc", "Bitcoin", new BigDecimal("60000"), new BigDecimal("1.2"), "$", "")
        );
    }
}
