package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class BacktestService {

    private static final Logger log = LoggerFactory.getLogger(BacktestService.class);

    private static final int FOUR_MONTHS_DAYS = 84;
    private static final int BACKTEST_HISTORY = 2500;
    private static final int MIN_BARS_FOR_ICHIMOKU = 100;
    private static final int COOLDOWN_DAYS = 20;
    private static final double CLOUD_EDGE_TOLERANCE = 0;

    private final MarketDataService marketDataService;
    private final IchimokuCalculator ichimokuCalc;

    public BacktestService(MarketDataService marketDataService, IchimokuCalculator ichimokuCalc) {
        this.marketDataService = marketDataService;
        this.ichimokuCalc = ichimokuCalc;
    }

    public BacktestResult run(boolean gpw, boolean us, List<String> forexPairs, int usTopN,
                              java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        marketDataService.resetForNewScan();

        List<String[]> allSymbols = new ArrayList<>();
        if (gpw) ScreenerService.GPW_SYMBOLS.forEach(s -> allSymbols.add(new String[]{s, "GPW"}));
        if (us) ScreenerService.US_SYMBOLS.stream().limit(usTopN).forEach(s -> allSymbols.add(new String[]{s, "US"}));
        if (forexPairs != null) forexPairs.forEach(s -> allSymbols.add(new String[]{s, "FOREX"}));

        log.info("Backtest starting for {} symbols (SL trails cloud edge, no max period)", allSymbols.size());

        BacktestResult result = new BacktestResult();
        ExecutorService exec = Executors.newFixedThreadPool(6);
        List<Future<List<TradeRecord>>> futures = new ArrayList<>();

        for (String[] sym : allSymbols) {
            futures.add(exec.submit(() -> backtestSymbol(sym[0], sym[1], dateFrom, dateTo)));
        }
        exec.shutdown();

        for (int i = 0; i < futures.size(); i++) {
            try {
                List<TradeRecord> trades = futures.get(i).get(120, TimeUnit.SECONDS);
                result.getTrades().addAll(trades);
            } catch (Exception e) {
                result.getErrors().add(allSymbols.get(i)[0] + ": " + e.getMessage());
            }
        }

        result.getTrades().sort(Comparator.comparing(TradeRecord::getEntryDate));
        computeAggregates(result);
        log.info("Backtest done: {} trades, win rate {}%", result.getTotalTrades(), String.format("%.1f", result.getWinRatePct()));
        return result;
    }

    private List<TradeRecord> backtestSymbol(String symbol, String market,
                                              java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        List<StockCandle> candles = marketDataService.downloadHistory(symbol, BACKTEST_HISTORY);
        if (candles.size() < MIN_BARS_FOR_ICHIMOKU + FOUR_MONTHS_DAYS) return List.of();

        List<IchimokuValues> ichimoku = ichimokuCalc.calculate(candles);
        int size = candles.size();

        List<TradeRecord> trades = new ArrayList<>();
        int cooldownUntil = 0;

        for (int i = MIN_BARS_FOR_ICHIMOKU + FOUR_MONTHS_DAYS; i < size; i++) {
            if (i <= cooldownUntil) continue;

            java.time.LocalDate barDate = candles.get(i).getDate();
            if (dateFrom != null && barDate.isBefore(dateFrom)) continue;
            if (dateTo != null && barDate.isAfter(dateTo)) continue;

            String signal = detectEntrySignal(candles, ichimoku, i);
            if (signal == null) continue;

            TradeRecord trade = simulateTrade(candles, ichimoku, i, signal);
            if (trade == null) continue;

            trade.setSymbol(symbol);
            trade.setMarket(market);
            trades.add(trade);

            int exitIdx = findBarIndex(candles, trade.getExitDate(), i);
            cooldownUntil = exitIdx + COOLDOWN_DAYS;
            i = exitIdx;
        }
        return trades;
    }

    // ───────── ENTRY SIGNAL DETECTION ─────────

    private String detectEntrySignal(List<StockCandle> candles, List<IchimokuValues> ichimoku, int idx) {
        double close = candles.get(idx).getClose();
        double prevClose = candles.get(idx - 1).getClose();
        IchimokuValues ichi = ichimoku.get(idx);
        IchimokuValues prevIchi = ichimoku.get(idx - 1);

        double cloudTop = ichi.getCloudTop();
        double cloudBottom = ichi.getCloudBottom();
        double prevCloudTop = prevIchi.getCloudTop();
        double prevCloudBottom = prevIchi.getCloudBottom();

        if (cloudTop == 0 || cloudBottom == 0 || prevCloudTop == 0 || prevCloudBottom == 0) return null;

        double topTol = cloudTop * CLOUD_EDGE_TOLERANCE;
        double botTol = cloudBottom * CLOUD_EDGE_TOLERANCE;

        double tenkan = ichi.getTenkanSen();
        double kijun = ichi.getKijunSen();
        if (tenkan == 0 || kijun == 0) return null;

        // LONG: today above cloud, yesterday was not
        boolean longBreakout = close > cloudTop + topTol && prevClose <= prevCloudTop + prevCloudTop * CLOUD_EDGE_TOLERANCE;
        if (longBreakout && tenkan > kijun) {
            int cloudDays = countCloudDaysBack(candles, ichimoku, idx - 1, true);
            if (cloudDays >= FOUR_MONTHS_DAYS) return "LONG";
        }

        // SHORT: today below cloud, yesterday was not
        boolean shortBreakout = close < cloudBottom - botTol && prevClose >= prevCloudBottom - prevCloudBottom * CLOUD_EDGE_TOLERANCE;
        if (shortBreakout && tenkan < kijun) {
            int cloudDays = countCloudDaysBack(candles, ichimoku, idx - 1, false);
            if (cloudDays >= FOUR_MONTHS_DAYS) return "SHORT";
        }

        return null;
    }

    private int countCloudDaysBack(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                    int fromIdx, boolean longSide) {
        int count = 0;
        for (int i = fromIdx; i >= 0; i--) {
            double val = longSide ? ichimoku.get(i).getCloudTop() : ichimoku.get(i).getCloudBottom();
            if (val == 0) continue;
            double tol = val * CLOUD_EDGE_TOLERANCE;
            boolean ok = longSide
                    ? candles.get(i).getClose() < val + tol
                    : candles.get(i).getClose() > val - tol;
            if (ok) count++;
            else break;
        }
        return count;
    }

    // ───────── TRADE SIMULATION: SL trails opposite cloud edge ─────────

    private TradeRecord simulateTrade(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                       int entryIdx, String direction) {
        int size = candles.size();
        boolean isLong = "LONG".equals(direction);

        double entryPrice = candles.get(entryIdx).getClose();
        IchimokuValues entryIchi = ichimoku.get(entryIdx);
        double initialSl = isLong ? entryIchi.getCloudBottom() : entryIchi.getCloudTop();

        if (initialSl == 0) return null;
        double risk = Math.abs(entryPrice - initialSl);
        if (risk < entryPrice * 0.001) return null;

        TradeRecord trade = new TradeRecord();
        trade.setDirection(direction);
        trade.setEntryDate(candles.get(entryIdx).getDate());
        trade.setEntryPrice(entryPrice);
        trade.setInitialSl(initialSl);
        trade.setCloudTopAtEntry(entryIchi.getCloudTop());
        trade.setCloudBottomAtEntry(entryIchi.getCloudBottom());

        double sl = initialSl;
        int slTrails = 0;

        for (int i = entryIdx + 1; i < size; i++) {
            StockCandle bar = candles.get(i);

            // ── Check SL hit ──
            if (isLong && bar.getLow() <= sl) {
                return closeTrade(trade, bar.getDate(), sl, entryPrice, risk, isLong, "SL_HIT", i - entryIdx, slTrails);
            }
            if (!isLong && bar.getHigh() >= sl) {
                return closeTrade(trade, bar.getDate(), sl, entryPrice, risk, isLong, "SL_HIT", i - entryIdx, slTrails);
            }

            // ── Trail SL along opposite cloud edge ──
            IchimokuValues ichi = ichimoku.get(i);
            if (isLong) {
                double cloudBot = ichi.getCloudBottom();
                if (cloudBot > 0 && cloudBot > sl) {
                    sl = cloudBot;
                    slTrails++;
                }
            } else {
                double cloudTop = ichi.getCloudTop();
                if (cloudTop > 0 && cloudTop < sl) {
                    sl = cloudTop;
                    slTrails++;
                }
            }
        }

        // Reached end of data without SL hit
        int exitIdx = size - 1;
        double exitPrice = candles.get(exitIdx).getClose();
        return closeTrade(trade, candles.get(exitIdx).getDate(), exitPrice, entryPrice, risk, isLong, "DATA_END", exitIdx - entryIdx, slTrails);
    }

    // ───────── HELPERS ─────────

    private TradeRecord closeTrade(TradeRecord trade, java.time.LocalDate exitDate, double exitPrice,
                                    double entryPrice, double risk, boolean isLong,
                                    String reason, int holdingDays, int slTrails) {
        trade.setExitDate(exitDate);
        trade.setExitPrice(exitPrice);
        trade.setFinalSl(exitPrice); // at exit, SL = exit price
        trade.setExitReason(reason);
        trade.setHoldingDays(holdingDays);
        trade.setSlTrailCount(slTrails);

        double pnl = isLong ? exitPrice - entryPrice : entryPrice - exitPrice;
        trade.setReturnPct((pnl / entryPrice) * 100.0);
        trade.setRMultiple(risk != 0 ? pnl / risk : 0);

        trade.setStooqUrl(buildStooqUrl(trade.getSymbol(), trade.getEntryDate(), exitDate));

        return trade;
    }

    private String buildStooqUrl(String symbol, java.time.LocalDate entryDate, java.time.LocalDate exitDate) {
        if (symbol == null || entryDate == null) return null;
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd");
        java.time.LocalDate d1 = entryDate.minusDays(120);
        java.time.LocalDate d2 = (exitDate != null ? exitDate : entryDate).plusDays(120);
        return "https://stooq.com/q/?s=" + symbol.toLowerCase()
                + "&d1=" + d1.format(fmt)
                + "&d2=" + d2.format(fmt)
                + "&i=d";
    }

    private int findBarIndex(List<StockCandle> candles, java.time.LocalDate date, int searchFrom) {
        for (int i = searchFrom; i < candles.size(); i++) {
            if (!candles.get(i).getDate().isBefore(date)) return i;
        }
        return candles.size() - 1;
    }

    private void computeAggregates(BacktestResult r) {
        List<TradeRecord> trades = r.getTrades();
        r.setTotalTrades(trades.size());
        if (trades.isEmpty()) return;

        int wins = 0, slHits = 0;
        double sumReturn = 0, sumWin = 0, sumLoss = 0, sumR = 0, sumHold = 0;
        double grossProfit = 0, grossLoss = 0;
        double maxDd = 0;

        for (TradeRecord t : trades) {
            sumReturn += t.getReturnPct();
            sumR += t.getRMultiple();
            sumHold += t.getHoldingDays();

            if (t.getReturnPct() > 0) {
                wins++;
                sumWin += t.getReturnPct();
                grossProfit += t.getReturnPct();
            } else {
                sumLoss += t.getReturnPct();
                grossLoss += Math.abs(t.getReturnPct());
            }

            if ("SL_HIT".equals(t.getExitReason())) slHits++;
            maxDd = Math.min(maxDd, t.getReturnPct());
        }

        int n = trades.size();
        r.setWins(wins);
        r.setLosses(n - wins);
        r.setWinRatePct((wins * 100.0) / n);
        r.setAvgReturnPct(sumReturn / n);
        r.setAvgWinPct(wins > 0 ? sumWin / wins : 0);
        r.setAvgLossPct((n - wins) > 0 ? sumLoss / (n - wins) : 0);
        r.setMaxDrawdownPct(maxDd);
        r.setProfitFactor(grossLoss > 0 ? grossProfit / grossLoss : grossProfit > 0 ? 999 : 0);
        r.setAvgRMultiple(sumR / n);
        r.setAvgHoldingDays(sumHold / n);
        r.setSlHitRatePct((slHits * 100.0) / n);
    }
}
