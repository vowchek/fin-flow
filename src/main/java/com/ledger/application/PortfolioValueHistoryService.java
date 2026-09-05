package com.ledger.application;

import com.ledger.api.dto.ValuePointResponse;
import com.ledger.domain.AssetMarket;
import com.ledger.domain.CatalogItem;
import com.ledger.domain.TradeSide;
import com.ledger.domain.TxKind;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

@Service
public class PortfolioValueHistoryService {

    private static final int MAX_POINTS = 366;

    private final QuoteService quotes;
    private final CatalogService catalog;

    public PortfolioValueHistoryService(QuoteService quotes, CatalogService catalog) {
        this.quotes = quotes;
        this.catalog = catalog;
    }

    @Transactional
    public List<ValuePointResponse> build(AssetMarket market, List<PositionTrades> positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }

        LocalDate start = positions.stream()
                .flatMap(p -> p.trades().stream())
                .map(TradeLeg::occurredOn)
                .min(LocalDate::compareTo)
                .orElse(null);
        if (start == null) {
            return List.of();
        }
        LocalDate end = LocalDate.now();
        if (start.isAfter(end)) {
            return List.of();
        }

        String cashSymbol = CashSupport.symbol(market);
        Map<String, NavigableMap<LocalDate, BigDecimal>> prices = new HashMap<>();
        for (PositionTrades position : positions) {
            if (cashSymbol.equalsIgnoreCase(position.symbol())) {
                NavigableMap<LocalDate, BigDecimal> cashSeries = new TreeMap<>();
                cashSeries.put(start, BigDecimal.ONE);
                prices.put(position.symbol(), cashSeries);
                continue;
            }
            CatalogItem item;
            try {
                item = catalog.requireBySymbol(market, position.symbol());
            } catch (RuntimeException ex) {
                continue;
            }
            quotes.ensureHistory(item, start, end);
            prices.put(position.symbol(), quotes.priceSeries(item, start, end));
        }

        List<TradeEvent> timeline = positions.stream()
                .flatMap(p -> p.trades().stream()
                        .map(t -> new TradeEvent(
                                p.symbol(),
                                t.side(),
                                t.kind() == null ? TxKind.TRADE : t.kind(),
                                t.quantity(),
                                t.unitPrice(),
                                t.occurredOn()
                        )))
                .sorted(Comparator
                        .comparing(TradeEvent::occurredOn)
                        .thenComparing(TradeEvent::symbol))
                .toList();

        long days = ChronoUnit.DAYS.between(start, end) + 1;
        int step = days <= MAX_POINTS ? 1 : (int) Math.ceil(days / (double) MAX_POINTS);

        Map<String, BigDecimal> qty = new HashMap<>();
        Map<String, BigDecimal> cost = new HashMap<>();
        int tradeIdx = 0;
        List<ValuePointResponse> points = new ArrayList<>();

        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(step)) {
            tradeIdx = applyTradesUntil(timeline, tradeIdx, day, qty, cost);
            ValuePointResponse point = snapshot(day, qty, cost, prices);
            if (point != null) {
                points.add(point);
            }
        }

        if (step > 1 && (points.isEmpty() || !points.getLast().date().equals(end))) {
            applyTradesUntil(timeline, tradeIdx, end, qty, cost);
            ValuePointResponse point = snapshot(end, qty, cost, prices);
            if (point != null) {
                points.add(point);
            }
        }

        return points;
    }

    private static int applyTradesUntil(
            List<TradeEvent> timeline,
            int tradeIdx,
            LocalDate day,
            Map<String, BigDecimal> qty,
            Map<String, BigDecimal> cost
    ) {
        while (tradeIdx < timeline.size() && !timeline.get(tradeIdx).occurredOn().isAfter(day)) {
            applyTrade(timeline.get(tradeIdx++), qty, cost);
        }
        return tradeIdx;
    }

    private static void applyTrade(TradeEvent trade, Map<String, BigDecimal> qty, Map<String, BigDecimal> cost) {
        BigDecimal q = qty.getOrDefault(trade.symbol(), BigDecimal.ZERO);
        BigDecimal c = cost.getOrDefault(trade.symbol(), BigDecimal.ZERO);
        TxKind kind = trade.kind() == null ? TxKind.TRADE : trade.kind();

        switch (kind) {
            case DEPOSIT -> {
                q = q.add(trade.quantity());
                c = c.add(trade.quantity());
            }
            case WITHDRAW -> {
                q = q.subtract(trade.quantity());
                c = c.subtract(trade.quantity());
                if (q.compareTo(BigDecimal.ZERO) <= 0) {
                    q = BigDecimal.ZERO;
                    c = BigDecimal.ZERO;
                } else if (c.compareTo(BigDecimal.ZERO) < 0) {
                    c = BigDecimal.ZERO;
                }
            }
            case DIVIDEND, COUPON -> q = q.add(trade.quantity());
            case TRADE -> {
                if (trade.side() == TradeSide.BUY) {
                    q = q.add(trade.quantity());
                    if (trade.unitPrice() != null) {
                        c = c.add(trade.quantity().multiply(trade.unitPrice()));
                    }
                } else {
                    if (q.compareTo(BigDecimal.ZERO) > 0 && trade.unitPrice() != null) {
                        BigDecimal avg = c.divide(q, 8, RoundingMode.HALF_UP);
                        BigDecimal sellQty = trade.quantity().min(q);
                        c = c.subtract(avg.multiply(sellQty)).max(BigDecimal.ZERO);
                    }
                    q = q.subtract(trade.quantity());
                    if (q.compareTo(BigDecimal.ZERO) <= 0) {
                        q = BigDecimal.ZERO;
                        c = BigDecimal.ZERO;
                    }
                }
            }
        }

        qty.put(trade.symbol(), q);
        cost.put(trade.symbol(), c);
    }

    private static ValuePointResponse snapshot(
            LocalDate day,
            Map<String, BigDecimal> qty,
            Map<String, BigDecimal> cost,
            Map<String, NavigableMap<LocalDate, BigDecimal>> prices
    ) {
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal totalInvested = BigDecimal.ZERO;
        boolean any = false;
        for (Map.Entry<String, BigDecimal> entry : qty.entrySet()) {
            if (entry.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            any = true;
            totalInvested = totalInvested.add(cost.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            NavigableMap<LocalDate, BigDecimal> series = prices.get(entry.getKey());
            if (series == null || series.isEmpty()) {
                continue;
            }
            Map.Entry<LocalDate, BigDecimal> price = series.floorEntry(day);
            if (price == null) {
                continue;
            }
            totalValue = totalValue.add(entry.getValue().multiply(price.getValue()));
        }
        if (!any) {
            return null;
        }
        return new ValuePointResponse(day, totalValue, totalInvested);
    }

    public record PositionTrades(String symbol, List<TradeLeg> trades) {
    }

    public record TradeLeg(TradeSide side, TxKind kind, BigDecimal quantity, BigDecimal unitPrice, LocalDate occurredOn) {
        public TradeLeg(TradeSide side, BigDecimal quantity, BigDecimal unitPrice, LocalDate occurredOn) {
            this(side, TxKind.TRADE, quantity, unitPrice, occurredOn);
        }
    }

    private record TradeEvent(
            String symbol,
            TradeSide side,
            TxKind kind,
            BigDecimal quantity,
            BigDecimal unitPrice,
            LocalDate occurredOn
    ) {
    }
}
