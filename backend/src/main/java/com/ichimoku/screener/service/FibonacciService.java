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
public class FibonacciService {

    private static final Logger log = LoggerFactory.getLogger(FibonacciService.class);

    private static final int LOOKBACK_DAYS = 400;
    private static final int SIX_MONTHS_BARS = 130;
    private static final double RETRACEMENT_THRESHOLD = 0.10;
    private static final double FIB50_ZONE_PCT = 0.03;
    private static final double MIN_IMPULSE_PCT = 0.10;
    private static final double SWING_THRESHOLD_ATR_MULT = 1.5;
    private static final int ATR_PERIOD = 14;
    private static final double MIN_DIRECTIONAL_EFFICIENCY = 0.35;

    private final MarketDataService marketDataService;

    public FibonacciService(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    public FibonacciResponse runScan(boolean includeGpw, boolean includeUs,
                                      List<String> forexPairs,
                                      int turnoverDays, double minTurnover, int usTopN) {
        marketDataService.resetForNewScan();
        FibonacciResponse response = new FibonacciResponse();
        List<FibonacciEntry> allEntries = Collections.synchronizedList(new ArrayList<>());
        List<AnalysisLogEntry> analysisLog = Collections.synchronizedList(new ArrayList<>());

        List<String[]> allSymbols = new ArrayList<>();
        if (includeGpw) ScreenerService.GPW_SYMBOLS.forEach(s -> allSymbols.add(new String[]{s, "GPW"}));
        if (includeUs) ScreenerService.US_SYMBOLS.stream().limit(usTopN).forEach(s -> allSymbols.add(new String[]{s, "US"}));
        if (forexPairs != null) forexPairs.forEach(s -> allSymbols.add(new String[]{s, "FOREX"}));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        for (String[] sym : allSymbols) {
            futures.add(executor.submit(() -> {
                try {
                    Thread.sleep(200);
                    FibonacciEntry result = analyzeSymbol(sym[0], sym[1], turnoverDays, minTurnover);
                    if (result != null) {
                        allEntries.add(result);
                        analysisLog.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "OK",
                                result.getDirection() + " col" + result.getColumn()));
                    } else {
                        analysisLog.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "NO_SIGNAL", ""));
                    }
                } catch (Exception e) {
                    log.warn("Fib error analyzing {}: {}", sym[0], e.getMessage());
                    analysisLog.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "ERROR", e.getMessage()));
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(90, TimeUnit.SECONDS); } catch (Exception e) { /* skip */ }
        }
        executor.shutdown();

        for (FibonacciEntry entry : allEntries) {
            switch (entry.getColumn()) {
                case 1 -> response.getColumn1().add(entry);
                case 2 -> response.getColumn2().add(entry);
                case 3 -> response.getColumn3().add(entry);
            }
        }

        response.getColumn1().sort(Comparator.comparingDouble(FibonacciEntry::getImpulsePct).reversed());
        response.getColumn2().sort(Comparator.comparingDouble(FibonacciEntry::getRetracementPct));
        response.getColumn3().sort(Comparator.comparingDouble(e -> Math.abs(e.getDistToFib50Pct())));
        response.setLog(analysisLog);
        response.setDataSource(marketDataService.getActiveProviderName());

        return response;
    }

    public FibChartData getChartData(String symbol) {
        marketDataService.resetForNewScan();
        List<StockCandle> candles = marketDataService.downloadHistory(symbol, LOOKBACK_DAYS);
        if (candles.size() < 60) return null;

        FibonacciEntry entry = analyzeSymbol(symbol, "", 0, 0);
        if (entry == null) return null;

        int limit = Math.min(candles.size(), 200);
        int from = candles.size() - limit;

        List<FibChartData.CandlePoint> candlePoints = new ArrayList<>();
        for (int i = from; i < candles.size(); i++) {
            StockCandle c = candles.get(i);
            candlePoints.add(new FibChartData.CandlePoint(
                    c.getDate().toString(), c.getOpen(), c.getHigh(), c.getLow(), c.getClose()));
        }

        FibChartData data = new FibChartData();
        data.setSymbol(symbol.toUpperCase());
        data.setCandles(candlePoints);
        data.setDirection(entry.getDirection());
        data.setImpulseStartDate(entry.getImpulseStartDate());
        data.setImpulseEndDate(entry.getImpulseEndDate());
        data.setImpulseStartPrice(entry.getImpulseStartPrice());
        data.setImpulseEndPrice(entry.getImpulseEndPrice());
        data.setFib236(entry.getFib236());
        data.setFib382(entry.getFib382());
        data.setFib500(entry.getFib500());
        data.setFib618(entry.getFib618());
        data.setFib786(entry.getFib786());
        data.setStopLoss(entry.getStopLoss());
        data.setInPlay(entry.isInPlay());
        return data;
    }

    FibonacciEntry analyzeSymbol(String symbol, String market, int turnoverDays, double minTurnover) {
        List<StockCandle> candles = marketDataService.downloadHistory(symbol, LOOKBACK_DAYS);
        if (candles.size() < 60) return null;

        boolean isForex = "FOREX".equals(market);
        if (!isForex && minTurnover > 0) {
            double avgTurnover = calcAvgTurnover(candles, turnoverDays);
            if (avgTurnover < minTurnover) return null;
        }

        double atr = calcATR(candles, ATR_PERIOD);
        double swingThreshold = atr * SWING_THRESHOLD_ATR_MULT;

        List<int[]> swings = detectSwingPoints(candles, swingThreshold);
        if (swings.size() < 2) return null;

        int size = candles.size();
        int sixMonthsAgo = Math.max(0, size - SIX_MONTHS_BARS);

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
                startPrice = candles.get(swStart[0]).getLow();
                endPrice = candles.get(swEnd[0]).getHigh();
                dir = "BULLISH";
            } else if (swStart[1] == 1 && swEnd[1] == -1) {
                startPrice = candles.get(swStart[0]).getHigh();
                endPrice = candles.get(swEnd[0]).getLow();
                dir = "BEARISH";
            } else {
                continue;
            }

            double range = Math.abs(endPrice - startPrice);
            if (range > bestRange) {
                double efficiency = directionalEfficiency(candles, swStart[0], swEnd[0], dir);
                if (efficiency >= MIN_DIRECTIONAL_EFFICIENCY) {
                    bestRange = range;
                    bestImpulse = new int[]{swStart[0], swEnd[0]};
                    bestDir = dir;
                }
            }
        }

        if (bestDir == null || bestRange <= 0) return null;

        int startIdx = bestImpulse[0];
        int endIdx = bestImpulse[1];
        double impulseStart, impulseEnd;

        if ("BULLISH".equals(bestDir)) {
            impulseStart = candles.get(startIdx).getLow();
            impulseEnd = candles.get(endIdx).getHigh();
        } else {
            impulseStart = candles.get(startIdx).getHigh();
            impulseEnd = candles.get(endIdx).getLow();
        }

        double impulseHeight = Math.abs(impulseEnd - impulseStart);
        double impulsePct = impulseHeight / impulseStart;
        if (impulsePct < MIN_IMPULSE_PCT) return null;

        int impulseBars = endIdx - startIdx;
        int correctionBars = size - 1 - endIdx;
        if (correctionBars >= impulseBars) {
            log.debug("{}: impulse {}bars < correction {}bars — skipping", symbol, impulseBars, correctionBars);
            return null;
        }

        double fib236, fib382, fib500, fib618, fib786;
        if ("BULLISH".equals(bestDir)) {
            fib236 = impulseEnd - impulseHeight * 0.236;
            fib382 = impulseEnd - impulseHeight * 0.382;
            fib500 = impulseEnd - impulseHeight * 0.500;
            fib618 = impulseEnd - impulseHeight * 0.618;
            fib786 = impulseEnd - impulseHeight * 0.786;
        } else {
            fib236 = impulseEnd + impulseHeight * 0.236;
            fib382 = impulseEnd + impulseHeight * 0.382;
            fib500 = impulseEnd + impulseHeight * 0.500;
            fib618 = impulseEnd + impulseHeight * 0.618;
            fib786 = impulseEnd + impulseHeight * 0.786;
        }

        double lastClose = candles.get(size - 1).getClose();

        double retracementAbs;
        if ("BULLISH".equals(bestDir)) {
            retracementAbs = impulseEnd - lastClose;
        } else {
            retracementAbs = lastClose - impulseEnd;
        }
        retracementAbs = Math.max(0, retracementAbs);
        double retracementPct = impulseHeight > 0 ? (retracementAbs / impulseHeight) * 100 : 0;
        double retracementFromPeak = impulseEnd > 0 ? (retracementAbs / impulseEnd) * 100 : 0;

        double distToFib50 = Math.abs(lastClose - fib500);
        double distToFib50Pct = lastClose > 0 ? (distToFib50 / lastClose) * 100 : 0;

        String nearestFib = findNearestFib(lastClose, fib236, fib382, fib500, fib618, fib786);

        int column;
        if (retracementPct < RETRACEMENT_THRESHOLD * 100) {
            column = 1;
        } else if (retracementPct < 45) {
            column = 2;
        } else if (retracementPct >= 45 && retracementPct <= 55) {
            column = 3;
        } else if (retracementPct < 70) {
            column = 2;
        } else {
            return null;
        }

        if (column == 2 && distToFib50Pct < FIB50_ZONE_PCT * 100) {
            column = 3;
        }

        FibonacciEntry entry = new FibonacciEntry();
        entry.setSymbol(symbol.toUpperCase());
        entry.setMarket(market);
        entry.setDirection(bestDir);
        entry.setLastClose(lastClose);
        entry.setImpulseStartDate(candles.get(startIdx).getDate().toString());
        entry.setImpulseEndDate(candles.get(endIdx).getDate().toString());
        entry.setImpulseStartPrice(impulseStart);
        entry.setImpulseEndPrice(impulseEnd);
        entry.setImpulseHeight(impulseHeight);
        entry.setImpulsePct(impulsePct * 100);
        entry.setImpulseDays((int) ChronoUnit.DAYS.between(
                candles.get(startIdx).getDate(), candles.get(endIdx).getDate()));
        entry.setFib236(fib236);
        entry.setFib382(fib382);
        entry.setFib500(fib500);
        entry.setFib618(fib618);
        entry.setFib786(fib786);
        entry.setRetracementPct(retracementPct);
        entry.setRetracementFromPeak(retracementFromPeak);
        entry.setNearestFibLevel(nearestFib);
        entry.setDistToFib50Pct(distToFib50Pct);
        entry.setColumn(column);

        List<String> patterns = CandlePatternDetector.detectAll(candles, bestDir);
        entry.setPatterns(patterns);

        detectFibBounceSetup(candles, endIdx, fib618, fib786, bestDir, entry);

        buildSignals(entry);

        return entry;
    }

    /**
     * Detect swing highs and lows using ATR-based threshold.
     * Returns list of [barIndex, type] where type: 1=high, -1=low.
     */
    private List<int[]> detectSwingPoints(List<StockCandle> candles, double threshold) {
        List<int[]> swings = new ArrayList<>();
        int size = candles.size();
        if (size < 3) return swings;

        int dir = 0;
        double lastHigh = candles.get(0).getHigh();
        double lastLow = candles.get(0).getLow();
        int lastHighIdx = 0;
        int lastLowIdx = 0;

        for (int i = 1; i < size; i++) {
            double high = candles.get(i).getHigh();
            double low = candles.get(i).getLow();

            if (dir >= 0) {
                if (high > lastHigh) {
                    lastHigh = high;
                    lastHighIdx = i;
                }
                if (lastHigh - low >= threshold) {
                    swings.add(new int[]{lastHighIdx, 1});
                    dir = -1;
                    lastLow = low;
                    lastLowIdx = i;
                }
            }

            if (dir <= 0) {
                if (low < lastLow) {
                    lastLow = low;
                    lastLowIdx = i;
                }
                if (high - lastLow >= threshold) {
                    swings.add(new int[]{lastLowIdx, -1});
                    dir = 1;
                    lastHigh = high;
                    lastHighIdx = i;
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

    /**
     * Directional efficiency: net price change / sum of absolute bar-to-bar changes.
     * A clean impulse has efficiency close to 1; a choppy move close to 0.
     */
    private double directionalEfficiency(List<StockCandle> candles, int from, int to, String dir) {
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
            double high = candles.get(i).getHigh();
            double low = candles.get(i).getLow();
            double prevClose = candles.get(i - 1).getClose();
            double tr = Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            sum += tr;
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    private double calcAvgTurnover(List<StockCandle> candles, int days) {
        int size = candles.size();
        int from = Math.max(0, size - days);
        double sum = 0;
        int count = 0;
        for (int i = from; i < size; i++) {
            sum += candles.get(i).getTurnover();
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    private String findNearestFib(double price, double fib236, double fib382,
                                   double fib500, double fib618, double fib786) {
        double[] levels = {fib236, fib382, fib500, fib618, fib786};
        String[] names = {"23.6%", "38.2%", "50%", "61.8%", "78.6%"};
        int closest = 0;
        double minDist = Math.abs(price - levels[0]);
        for (int i = 1; i < levels.length; i++) {
            double dist = Math.abs(price - levels[i]);
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return names[closest];
    }

    /**
     * Scan candles after impulse end for a price-action pattern near the 61.8% fib level.
     * The pattern's extreme (low for bullish, high for bearish) becomes the stop loss.
     * Setup stays "in play" as long as price hasn't breached the SL.
     */
    private void detectFibBounceSetup(List<StockCandle> candles, int impulseEndIdx,
                                       double fib618, double fib786, String dir,
                                       FibonacciEntry entry) {
        int size = candles.size();
        double fibZoneMargin = Math.abs(fib786 - fib618) * 0.3;

        int bounceIdx = -1;
        List<String> bouncePatterns = null;
        double stopLoss = 0;

        for (int i = Math.max(impulseEndIdx + 3, 3); i < size; i++) {
            boolean inFibZone;
            if ("BULLISH".equals(dir)) {
                double low = candles.get(i).getLow();
                double prevLow = candles.get(i - 1).getLow();
                inFibZone = (low <= fib618 + fibZoneMargin && low >= fib786 - fibZoneMargin)
                         || (prevLow <= fib618 + fibZoneMargin && prevLow >= fib786 - fibZoneMargin);
            } else {
                double high = candles.get(i).getHigh();
                double prevHigh = candles.get(i - 1).getHigh();
                inFibZone = (high >= fib618 - fibZoneMargin && high <= fib786 + fibZoneMargin)
                         || (prevHigh >= fib618 - fibZoneMargin && prevHigh <= fib786 + fibZoneMargin);
            }

            if (!inFibZone) continue;

            List<StockCandle> window = candles.subList(0, i + 1);
            List<String> pats = CandlePatternDetector.detectAll(window, dir);
            if (!pats.isEmpty()) {
                bounceIdx = i;
                bouncePatterns = pats;

                if ("BULLISH".equals(dir)) {
                    stopLoss = Double.MAX_VALUE;
                    for (int j = Math.max(0, i - 2); j <= i; j++) {
                        stopLoss = Math.min(stopLoss, candles.get(j).getLow());
                    }
                } else {
                    stopLoss = 0;
                    for (int j = Math.max(0, i - 2); j <= i; j++) {
                        stopLoss = Math.max(stopLoss, candles.get(j).getHigh());
                    }
                }
            }
        }

        if (bounceIdx < 0) {
            entry.setFibBounce(false);
            entry.setInPlay(false);
            return;
        }

        entry.setFibBounce(true);
        entry.setStopLoss(stopLoss);
        entry.setBouncePatternDate(candles.get(bounceIdx).getDate().toString());
        entry.getPatterns().addAll(bouncePatterns.stream()
                .filter(p -> !entry.getPatterns().contains(p)).toList());

        double lastClose = candles.get(size - 1).getClose();
        boolean slHit;
        if ("BULLISH".equals(dir)) {
            slHit = lastClose < stopLoss;
        } else {
            slHit = lastClose > stopLoss;
        }

        entry.setInPlay(!slHit);
    }

    private void buildSignals(FibonacciEntry e) {
        List<String> signals = e.getSignals();
        String dir = "BULLISH".equals(e.getDirection()) ? "wzrostowy" : "spadkowy";

        signals.add(String.format("Impuls %s: %.1f%% (%dd)", dir, e.getImpulsePct(), e.getImpulseDays()));
        signals.add(String.format("Retracement: %.1f%%", e.getRetracementPct()));
        signals.add(String.format("Najblizszy Fib: %s", e.getNearestFibLevel()));

        if (e.getColumn() == 3) {
            signals.add(String.format("Dystans do Fib 50%%: %.2f%%", e.getDistToFib50Pct()));
        }

        if (!e.getPatterns().isEmpty()) {
            signals.add("Pattern: " + String.join(", ", e.getPatterns()));
        }

        if (e.isFibBounce()) {
            if (e.isInPlay()) {
                signals.add(String.format("W GRZE — pattern na 61.8%% (%s), SL: %.4f",
                        e.getBouncePatternDate(), e.getStopLoss()));
            } else {
                signals.add(String.format("SL WYBITY — cena przebila SL %.4f", e.getStopLoss()));
            }
        }
    }
}
