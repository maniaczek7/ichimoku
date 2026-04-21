package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.StockCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    private static final Set<String> FOREX_SYMBOLS = Set.of(
            "eurusd", "usdjpy", "gbpusd", "usdchf", "audusd", "usdcad", "nzdusd",
            "cadjpy", "nzdjpy", "audjpy", "eurjpy", "cadchf", "chfjpy", "nzdchf",
            "eurgbp", "audchf", "gbpjpy", "eurchf", "audnzd", "nzdcad", "gbpchf",
            "eurcad", "euraud", "gbpnzd", "gbpaud", "eurnzd", "gbpcad",
            "usdpln", "eurpln", "gbppln", "chfpln", "cadpln", "audpln"
    );

    private final TwelveDataService twelveDataService;
    private final YahooFinanceService yahooService;
    private final int failoverThreshold;

    private final Map<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Set<String> circuitOpen = ConcurrentHashMap.newKeySet();
    private volatile String lastUsedProvider;

    public MarketDataService(
            TwelveDataService twelveDataService,
            YahooFinanceService yahooService,
            @Value("${screener.data-source.failover-threshold:5}") int failoverThreshold) {

        this.twelveDataService = twelveDataService;
        this.yahooService = yahooService;
        this.failoverThreshold = failoverThreshold;

        log.info("Data routing: Forex → TwelveData (fallback: Yahoo), Stocks → Yahoo (failover after {} failures)",
                failoverThreshold);
    }

    public List<StockCandle> downloadHistory(String symbol, int daysBack) {
        List<MarketDataProvider> providers = resolveProviders(symbol);

        for (MarketDataProvider provider : providers) {
            String circuitKey = provider.getName() + ":" + classifySymbol(symbol);
            if (circuitOpen.contains(circuitKey)) continue;

            List<StockCandle> result = provider.downloadHistory(symbol, daysBack);

            if (result != null && !result.isEmpty()) {
                resetFailures(circuitKey);
                lastUsedProvider = provider.getName();
                return result;
            }

            int failures = consecutiveFailures
                    .computeIfAbsent(circuitKey, k -> new AtomicInteger(0))
                    .incrementAndGet();

            if (failures >= failoverThreshold) {
                circuitOpen.add(circuitKey);
                log.warn("Circuit OPEN for '{}' after {} consecutive failures - switching to fallback",
                        circuitKey, failures);
            }
        }
        return new ArrayList<>();
    }

    public void resetForNewScan() {
        circuitOpen.clear();
        consecutiveFailures.values().forEach(a -> a.set(0));
        lastUsedProvider = null;
        log.debug("Circuit breakers reset for new scan");
    }

    public String getActiveProviderName() {
        return lastUsedProvider != null ? lastUsedProvider : "none";
    }

    public String getLastUsedProvider() {
        return lastUsedProvider;
    }

    private List<MarketDataProvider> resolveProviders(String symbol) {
        List<MarketDataProvider> providers = new ArrayList<>();
        if (isForex(symbol)) {
            providers.add(twelveDataService);
            providers.add(yahooService);
        } else {
            providers.add(yahooService);
        }
        return providers;
    }

    static boolean isForex(String symbol) {
        if (symbol == null) return false;
        String s = symbol.toLowerCase().trim();
        if (FOREX_SYMBOLS.contains(s)) return true;
        return s.length() == 6 && s.chars().allMatch(Character::isLetter) && !s.contains(".");
    }

    private String classifySymbol(String symbol) {
        return isForex(symbol) ? "forex" : "stock";
    }

    private void resetFailures(String key) {
        AtomicInteger counter = consecutiveFailures.get(key);
        if (counter != null) counter.set(0);
    }
}
