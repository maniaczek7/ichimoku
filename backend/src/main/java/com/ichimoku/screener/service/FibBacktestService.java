package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@Service
public class FibBacktestService {

    private static final Logger log = LoggerFactory.getLogger(FibBacktestService.class);

    private static final int BACKTEST_HISTORY = 2500;
    private static final int MIN_BARS = 130;
    private static final double SWING_ATR_MULT = 1.5;
    private static final int ATR_PERIOD = 14;
    private static final double MIN_DIRECTIONAL_EFFICIENCY = 0.35;
    private static final double MIN_IMPULSE_PCT = 0.10;
    private static final int COOLDOWN_BARS = 30;

    private final MarketDataService marketDataService;

    public FibBacktestService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public BacktestResult run(boolean gpw, boolean us, List<String> forexPairs, int usTopN,
                              LocalDate dateFrom, LocalDate dateTo) {
        marketDataService.resetForNewScan();

        List<String[]> allSymbols = new ArrayList<>();
        if (gpw) ScreenerService.GPW_SYMBOLS.forEach(s -> allSymbols.add(new String[]{s, "GPW"}));
        if (us) ScreenerService.US_SYMBOLS.stream().limit(usTopN).forEach(s -> allSymbols.add(new String[]{s, "US"}));
        if (forexPairs != null) forexPairs.forEach(s -> allSymbols.add(new String[]{s, "FOREX"}));

        log.info("Fib backtest starting for {} symbols", allSymbols.size());

        BacktestResult result = new BacktestResult();
        ExecutorService exec = Executors.newFixedThreadPool(4);
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
        log.info("Fib backtest done: {} trades, win rate {}%",
                result.getTotalTrades(), String.format("%.1f", result.getWinRatePct()));
        return result;
    }

    private List<TradeRecord> backtestSymbol(String symbol, String market,
                                              LocalDate dateFrom, LocalDate dateTo) {
        List<StockCandle> candles = marketDataService.downloadHistory(symbol, BACKTEST_HISTORY);
        if (candles.size() < MIN_BARS) return List.of();

        List<TradeRecord> trades = new ArrayList<>();
        int size = candles.size();
        int cooldownUntil = 0;

        for (int windowEnd = MIN_BARS; windowEnd < size - 10; windowEnd += 5) {
            if (windowEnd <= cooldownUntil) continue;

            LocalDate barDate = candles.get(windowEnd).getDate();
            if (dateFrom != null && barDate.isBefore(dateFrom)) continue;
            if (dateTo != null && barDate.isAfter(dateTo)) continue;

            List<StockCandle> window = candles.subList(0, windowEnd + 1);
            TradeRecord trade = findAndSimulateTrade(window, candles, windowEnd, symbol, market);
            if (trade != null) {
                trades.add(trade);
                cooldownUntil = windowEnd + COOLDOWN_BARS;
            }
        }
        return trades;
    }

    private TradeRecord findAndSimulateTrade(List<StockCandle> window, List<StockCandle> allCandles,
                                              int entryBarIdx, String symbol, String market) {
        int wSize = window.size();
        double atr = calcATR(window, ATR_PERIOD);
        double swingThreshold = atr * SWING_ATR_MULT;

        List<int[]> swings = detectSwingPoints(window, swingThreshold);
        if (swings.size() < 2) return null;

        int sixMonthsAgo = Math.max(0, wSize - 130);
        int[] bestImpulse = null;
        double bestRange = 0;
        String bestDir = null;

        for (int s = 0; s < swings.size() - 1; s++) {
            int[] swStart = swings.get(s);
            int[] swEnd = swings.get(s + 1);
            if (swEnd[0] < sixMonthsAgo && swStart[0] < sixMonthsAgo) continue;

            double startPrice, endPrice;
            String dir;

            if (swStart[1] == -1 && swEnd[1] == 1) {
                startPrice = window.get(swStart[0]).getLow();
                endPrice = window.get(swEnd[0]).getHigh();
                dir = "BULLISH";
            } else if (swStart[1] == 1 && swEnd[1] == -1) {
                startPrice = window.get(swStart[0]).getHigh();
                endPrice = window.get(swEnd[0]).getLow();
                dir = "BEARISH";
            } else {
                continue;
            }

            double range = Math.abs(endPrice - startPrice);
            if (range > bestRange) {
                double eff = directionalEfficiency(window, swStart[0], swEnd[0]);
                if (eff >= MIN_DIRECTIONAL_EFFICIENCY) {
                    bestRange = range;
                    bestImpulse = new int[]{swStart[0], swEnd[0]};
                    bestDir = dir;
                }
            }
        }

        if (bestDir == null) return null;

        int startIdx = bestImpulse[0];
        int endIdx = bestImpulse[1];
        double impulseStart, impulseEnd;

        if ("BULLISH".equals(bestDir)) {
            impulseStart = window.get(startIdx).getLow();
            impulseEnd = window.get(endIdx).getHigh();
        } else {
            impulseStart = window.get(startIdx).getHigh();
            impulseEnd = window.get(endIdx).getLow();
        }

        double impulseHeight = Math.abs(impulseEnd - impulseStart);
        if (impulseHeight / impulseStart < MIN_IMPULSE_PCT) return null;

        int impulseBars = endIdx - startIdx;
        int correctionBars = wSize - 1 - endIdx;
        if (correctionBars >= impulseBars) return null;

        double fib618 = "BULLISH".equals(bestDir)
                ? impulseEnd - impulseHeight * 0.618
                : impulseEnd + impulseHeight * 0.618;
        double fib786 = "BULLISH".equals(bestDir)
                ? impulseEnd - impulseHeight * 0.786
                : impulseEnd + impulseHeight * 0.786;

        double fibZoneMargin = Math.abs(fib786 - fib618) * 0.3;

        int lastBar = wSize - 1;
        boolean inFibZone;
        if ("BULLISH".equals(bestDir)) {
            double low = window.get(lastBar).getLow();
            inFibZone = (low <= fib618 + fibZoneMargin && low >= fib786 - fibZoneMargin);
        } else {
            double high = window.get(lastBar).getHigh();
            inFibZone = (high >= fib618 - fibZoneMargin && high <= fib786 + fibZoneMargin);
        }
        if (!inFibZone) return null;

        List<String> pats = CandlePatternDetector.detectAll(window, bestDir);
        if (pats.isEmpty()) return null;

        double sl;
        if ("BULLISH".equals(bestDir)) {
            sl = Double.MAX_VALUE;
            for (int j = Math.max(0, lastBar - 2); j <= lastBar; j++) {
                sl = Math.min(sl, window.get(j).getLow());
            }
        } else {
            sl = 0;
            for (int j = Math.max(0, lastBar - 2); j <= lastBar; j++) {
                sl = Math.max(sl, window.get(j).getHigh());
            }
        }

        double entryPrice = window.get(lastBar).getClose();
        double tp = impulseEnd;
        LocalDate entryDate = window.get(lastBar).getDate();

        return simulateTrade(allCandles, entryBarIdx, symbol, market, bestDir, entryPrice, sl, tp, entryDate);
    }

    private TradeRecord simulateTrade(List<StockCandle> candles, int entryIdx,
                                       String symbol, String market, String dir,
                                       double entryPrice, double sl, double tp,
                                       LocalDate entryDate) {
        int size = candles.size();
        double risk = Math.abs(entryPrice - sl);
        if (risk <= 0) return null;

        for (int i = entryIdx + 1; i < size; i++) {
            StockCandle c = candles.get(i);
            boolean slHit, tpHit;

            if ("BULLISH".equals(dir)) {
                slHit = c.getLow() <= sl;
                tpHit = c.getHigh() >= tp;
            } else {
                slHit = c.getHigh() >= sl;
                tpHit = c.getLow() <= tp;
            }

            if (slHit) {
                return buildTrade(symbol, market, dir, entryDate, entryPrice, sl,
                        c.getDate(), sl, "SL_HIT", risk);
            }

            if (tpHit) {
                return buildTrade(symbol, market, dir, entryDate, entryPrice, sl,
                        c.getDate(), tp, "TP_HIT", risk);
            }
        }

        StockCandle last = candles.get(size - 1);
        return buildTrade(symbol, market, dir, entryDate, entryPrice, sl,
                last.getDate(), last.getClose(), "DATA_END", risk);
    }

    private TradeRecord buildTrade(String symbol, String market, String dir,
                                    LocalDate entryDate, double entryPrice, double sl,
                                    LocalDate exitDate, double exitPrice, String reason, double risk) {
        TradeRecord t = new TradeRecord();
        t.setSymbol(symbol.toUpperCase());
        t.setMarket(market);
        t.setDirection(dir.equals("BULLISH") ? "LONG" : "SHORT");
        t.setEntryDate(entryDate);
        t.setEntryPrice(entryPrice);
        t.setInitialSl(sl);
        t.setFinalSl(sl);
        t.setExitDate(exitDate);
        t.setExitPrice(exitPrice);
        t.setExitReason(reason);
        t.setHoldingDays((int) ChronoUnit.DAYS.between(entryDate, exitDate));
        t.setSlTrailCount(0);

        double returnPct;
        if ("LONG".equals(t.getDirection())) {
            returnPct = ((exitPrice - entryPrice) / entryPrice) * 100;
        } else {
            returnPct = ((entryPrice - exitPrice) / entryPrice) * 100;
        }
        t.setReturnPct(returnPct);
        t.setRMultiple(risk > 0 ? (returnPct / 100 * entryPrice) / risk : 0);
        t.setStooqUrl("https://stooq.pl/q/a2/?s=" + symbol.toLowerCase());

        return t;
    }

    private void computeAggregates(BacktestResult r) {
        List<TradeRecord> trades = r.getTrades();
        r.setTotalTrades(trades.size());
        if (trades.isEmpty()) return;

        List<TradeRecord> wins = trades.stream().filter(t -> t.getReturnPct() > 0).toList();
        List<TradeRecord> losses = trades.stream().filter(t -> t.getReturnPct() <= 0).toList();

        r.setWins(wins.size());
        r.setLosses(losses.size());
        r.setWinRatePct((double) wins.size() / trades.size() * 100);
        r.setAvgReturnPct(trades.stream().mapToDouble(TradeRecord::getReturnPct).average().orElse(0));
        r.setAvgRMultiple(trades.stream().mapToDouble(TradeRecord::getRMultiple).average().orElse(0));
        r.setAvgHoldingDays(trades.stream().mapToDouble(TradeRecord::getHoldingDays).average().orElse(0));
        r.setAvgWinPct(wins.isEmpty() ? 0 : wins.stream().mapToDouble(TradeRecord::getReturnPct).average().orElse(0));
        r.setAvgLossPct(losses.isEmpty() ? 0 : losses.stream().mapToDouble(TradeRecord::getReturnPct).average().orElse(0));
        r.setMaxDrawdownPct(trades.stream().mapToDouble(TradeRecord::getReturnPct).min().orElse(0));

        double totalWin = wins.stream().mapToDouble(TradeRecord::getReturnPct).sum();
        double totalLoss = losses.stream().mapToDouble(t -> Math.abs(t.getReturnPct())).sum();
        r.setProfitFactor(totalLoss > 0 ? totalWin / totalLoss : 0);
        r.setSlHitRatePct((double) trades.stream().filter(t -> "SL_HIT".equals(t.getExitReason())).count() / trades.size() * 100);
    }

    // Reused from FibonacciService
    private List<int[]> detectSwingPoints(List<StockCandle> candles, double threshold) {
        List<int[]> swings = new ArrayList<>();
        int size = candles.size();
        if (size < 3) return swings;

        int dir = 0;
        double lastHigh = candles.get(0).getHigh();
        double lastLow = candles.get(0).getLow();
        int lastHighIdx = 0, lastLowIdx = 0;

        for (int i = 1; i < size; i++) {
            double high = candles.get(i).getHigh();
            double low = candles.get(i).getLow();

            if (dir >= 0) {
                if (high > lastHigh) { lastHigh = high; lastHighIdx = i; }
                if (lastHigh - low >= threshold) {
                    swings.add(new int[]{lastHighIdx, 1});
                    dir = -1; lastLow = low; lastLowIdx = i;
                }
            }
            if (dir <= 0) {
                if (low < lastLow) { lastLow = low; lastLowIdx = i; }
                if (high - lastLow >= threshold) {
                    swings.add(new int[]{lastLowIdx, -1});
                    dir = 1; lastHigh = high; lastHighIdx = i;
                }
            }
        }

        if (dir == 1) swings.add(new int[]{lastHighIdx, 1});
        else if (dir == -1) swings.add(new int[]{lastLowIdx, -1});

        swings.sort(Comparator.comparingInt(a -> a[0]));

        List<int[]> cleaned = new ArrayList<>();
        for (int[] sw : swings) {
            if (cleaned.isEmpty() || cleaned.get(cleaned.size() - 1)[1] != sw[1]) {
                cleaned.add(sw);
            } else {
                int[] prev = cleaned.get(cleaned.size() - 1);
                if (sw[1] == 1 && candles.get(sw[0]).getHigh() > candles.get(prev[0]).getHigh()) {
                    cleaned.set(cleaned.size() - 1, sw);
                } else if (sw[1] == -1 && candles.get(sw[0]).getLow() < candles.get(prev[0]).getLow()) {
                    cleaned.set(cleaned.size() - 1, sw);
                }
            }
        }
        return cleaned;
    }

    private double directionalEfficiency(List<StockCandle> candles, int from, int to) {
        if (from >= to) return 0;
        double netMove = Math.abs(candles.get(to).getClose() - candles.get(from).getClose());
        double pathLength = 0;
        for (int i = from + 1; i <= to; i++) {
            pathLength += Math.abs(candles.get(i).getClose() - candles.get(i - 1).getClose());
        }
        return pathLength > 0 ? netMove / pathLength : 0;
    }

    private double calcATR(List<StockCandle> candles, int period) {
        int size = candles.size();
        int start = Math.max(1, size - period);
        double sum = 0;
        int count = 0;
        for (int i = start; i < size; i++) {
            double h = candles.get(i).getHigh();
            double l = candles.get(i).getLow();
            double pc = candles.get(i - 1).getClose();
            sum += Math.max(h - l, Math.max(Math.abs(h - pc), Math.abs(l - pc)));
            count++;
        }
        return count > 0 ? sum / count : 0;
    }
}
