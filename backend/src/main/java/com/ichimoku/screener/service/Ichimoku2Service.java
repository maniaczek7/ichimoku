package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

@Service
public class Ichimoku2Service {
    private static final Logger log = LoggerFactory.getLogger(Ichimoku2Service.class);
    static final int FOUR_MONTHS = 84, HISTORY = 400, DISPLACEMENT = 26;

    private final MarketDataService marketData;
    private final IchimokuCalculator calc;

    public Ichimoku2Service(MarketDataService marketData, IchimokuCalculator calc) {
        this.marketData = marketData;
        this.calc = calc;
    }

    public Ichimoku2Response runScan(boolean gpw, boolean us, List<String> forex, int turnoverDays, double minTurnover, int usTopN) {
        marketData.resetForNewScan();
        Ichimoku2Response resp = new Ichimoku2Response();
        List<Ichimoku2Entry> all = Collections.synchronizedList(new ArrayList<>());
        List<AnalysisLogEntry> logs = Collections.synchronizedList(new ArrayList<>());

        List<String[]> syms = new ArrayList<>();
        if (gpw) ScreenerService.GPW_SYMBOLS.forEach(s -> syms.add(new String[]{s, "GPW"}));
        if (us) ScreenerService.US_SYMBOLS.stream().limit(usTopN).forEach(s -> syms.add(new String[]{s, "US"}));
        if (forex != null) forex.forEach(s -> syms.add(new String[]{s, "FOREX"}));

        ExecutorService ex = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();
        for (String[] sym : syms) {
            futures.add(ex.submit(() -> {
                try {
                    Thread.sleep(200);
                    Ichimoku2Entry e = analyze(sym[0], sym[1], turnoverDays, minTurnover);
                    if (e != null) { all.add(e); logs.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "OK", e.getDirection() + " " + e.getToken())); }
                    else logs.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "NO_SIGNAL", ""));
                } catch (Exception ex2) {
                    logs.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "ERROR", ex2.getMessage()));
                }
            }));
        }
        for (Future<?> f : futures) { try { f.get(90, TimeUnit.SECONDS); } catch (Exception ignored) {} }
        ex.shutdown();

        for (Ichimoku2Entry e : all) {
            int col = classifyColumn(e);
            if (col == 1) resp.getColumn1().add(e);
            else if (col == 2) resp.getColumn2().add(e);
            else if (col == 3) resp.getColumn3().add(e);
        }
        resp.getColumn1().sort(Comparator.comparingInt(Ichimoku2Entry::getCloudDays).reversed());
        resp.getColumn2().sort(Comparator.comparingInt(Ichimoku2Entry::getCloudDays).reversed());
        resp.getColumn3().sort(Comparator.comparingInt(Ichimoku2Entry::getScore).reversed()
                .thenComparing(Comparator.comparingInt(Ichimoku2Entry::getCloudDays).reversed()));
        resp.setLog(logs);
        resp.setDataSource(marketData.getActiveProviderName());
        return resp;
    }

    Ichimoku2Entry analyze(String symbol, String market, int turnoverDays, double minTurnover) {
        List<StockCandle> candles = marketData.downloadHistory(symbol, HISTORY);
        if (candles.size() < 100) return null;
        if (!"FOREX".equals(market)) {
            double avg = calcAvgTurnover(candles, turnoverDays);
            if (avg < minTurnover) return null;
        }
        List<IchimokuValues> ichi = calc.calculate(candles);
        int start = findFirstCloud(ichi);
        if (start < 0) return null;
        int size = candles.size();
        Ichimoku2Entry e = new Ichimoku2Entry();
        e.setSymbol(symbol.toUpperCase());
        e.setMarket(market);
        e.setLastClose(candles.get(size - 1).getClose());
        e.setAvgTurnover10d(calcAvgTurnover(candles, turnoverDays));
        analyzeSetup(candles, ichi, start, e);
        if (e.getDirection() == null || e.getCloudDays() < FOUR_MONTHS) return null;
        analyzeChikou(candles, e);
        analyzeKumoTwist(calc.getFutureCloud(candles), e);
        calcScore(e);
        buildSignals(e);
        return e;
    }

    void analyzeSetup(List<StockCandle> candles, List<IchimokuValues> ichi, int start, Ichimoku2Entry e) {
        int size = candles.size();
        int idx = size - 1;
        double close = candles.get(idx).getClose();
        double top = ichi.get(idx).getCloudTop(), bot = ichi.get(idx).getCloudBottom();
        if (top == 0 || bot == 0) return;
        e.setCloudTopValue(top);
        e.setCloudBottomValue(bot);

        int longDays = countQualified(candles, ichi, idx - 1, start, true);
        int shortDays = countQualified(candles, ichi, idx - 1, start, false);
        boolean longBrk = close > top, shortBrk = close < bot;
        int longBrkDays = longBrk ? countBreakoutDays(candles, ichi, idx, start, true) : 0;
        int shortBrkDays = shortBrk ? countBreakoutDays(candles, ichi, idx, start, false) : 0;

        String dir = null;
        int days = 0;
        if (longBrk && longDays >= FOUR_MONTHS && longDays >= shortDays) { dir = "LONG"; days = longDays; }
        else if (shortBrk && shortDays >= FOUR_MONTHS) { dir = "SHORT"; days = shortDays; }
        else if (longDays >= FOUR_MONTHS && longDays >= shortDays) { dir = "LONG"; days = longDays; }
        else if (shortDays >= FOUR_MONTHS) { dir = "SHORT"; days = shortDays; }
        if (dir == null) return;

        e.setDirection(dir);
        e.setCloudDays(days);
        e.setToken(resolveToken(close, top, bot, dir));

        StockCandle bar = candles.get(idx);
        boolean pierce = "LONG".equals(dir) ? bar.getLow() < bot && bar.getHigh() > top : bar.getHigh() > top && bar.getLow() < bot;
        e.setFullCloudPierce(pierce);
        if (pierce) {
            e.setSuggestedStopLoss("LONG".equals(dir) ? bar.getLow() : bar.getHigh());
            e.setSlType("BREAKOUT_CANDLE");
        } else {
            e.setSuggestedStopLoss("LONG".equals(dir) ? bot : top);
            e.setSlType("CLOUD_EDGE");
        }
    }

    static String resolveToken(double close, double top, double bot, String dir) {
        if ("LONG".equals(dir)) {
            if (close > top) return "ABOVE_CLOUD";
            if (close >= bot && close <= top) return "IN_CLOUD";
            return "BEFORE_CLOUD";
        }
        if (close < bot) return "BELOW_CLOUD";
        if (close >= bot && close <= top) return "IN_CLOUD";
        return "BEFORE_CLOUD";
    }

    static int classifyColumn(Ichimoku2Entry e) {
        return switch (e.getToken()) {
            case "ABOVE_CLOUD", "BELOW_CLOUD" -> 3;
            case "IN_CLOUD" -> 2;
            default -> 1;
        };
    }

    static int countQualified(List<StockCandle> c, List<IchimokuValues> i, int from, int to, boolean longSide) {
        int n = 0;
        for (int j = from; j >= to; j--) {
            double top = i.get(j).getCloudTop(), bot = i.get(j).getCloudBottom();
            if (top == 0 || bot == 0) continue;
            double cl = c.get(j).getClose();
            if (longSide) { if (cl > top) break; n++; }
            else { if (cl < bot) break; n++; }
        }
        return n;
    }

    static int countBreakoutDays(List<StockCandle> c, List<IchimokuValues> i, int from, int to, boolean longSide) {
        int n = 0;
        for (int j = from; j >= to; j--) {
            double ref = longSide ? i.get(j).getCloudTop() : i.get(j).getCloudBottom();
            if (ref == 0) continue;
            boolean ok = longSide ? c.get(j).getClose() > ref : c.get(j).getClose() < ref;
            if (ok) n++; else break;
        }
        return n;
    }

    static void analyzeChikou(List<StockCandle> candles, Ichimoku2Entry e) {
        int size = candles.size();
        if (size <= DISPLACEMENT) { e.setChikouConfirms(false); return; }
        double cur = candles.get(size - 1).getClose();
        double past = candles.get(size - 1 - DISPLACEMENT).getClose();
        e.setChikouValue(cur);
        e.setPriceAtChikou(past);
        e.setChikouConfirms("LONG".equals(e.getDirection()) ? cur > past : cur < past);
    }

    static void analyzeKumoTwist(double[][] future, Ichimoku2Entry e) {
        for (int i = 1; i < future.length; i++) {
            double pd = future[i-1][0] - future[i-1][1], cd = future[i][0] - future[i][1];
            if (future[i][0] == 0 || pd * cd >= 0) continue;
            boolean bull = cd > 0;
            if ("LONG".equals(e.getDirection()) && bull) { e.setKumoTwist(true); return; }
            if ("SHORT".equals(e.getDirection()) && !bull) { e.setKumoTwist(true); return; }
        }
        e.setKumoTwist(false);
    }

    static void calcScore(Ichimoku2Entry e) {
        if (!"ABOVE_CLOUD".equals(e.getToken()) && !"BELOW_CLOUD".equals(e.getToken())) { e.setScore(0); return; }
        int s = 1;
        if (e.isKumoTwist()) s++;
        if (e.isChikouConfirms()) s++;
        e.setScore(s);
    }

    static void buildSignals(Ichimoku2Entry e) {
        String side = "LONG".equals(e.getDirection()) ? "pod" : "nad";
        e.getSignals().add((e.getDirection().equals("LONG") ? "L" : "S") + ": " + e.getCloudDays() + "d " + side + " chmura");
        e.getSignals().add("Token: " + e.getToken());
        if (e.isKumoTwist()) e.getSignals().add("Kumo Twist");
        if (e.isChikouConfirms()) e.getSignals().add("Chikou potwierdza");
        if (e.isFullCloudPierce()) e.getSignals().add("Pelne przebicie chmury");
    }

    static int findFirstCloud(List<IchimokuValues> i) {
        for (int j = 0; j < i.size(); j++)
            if (i.get(j).getSenkouSpanA() != 0 && i.get(j).getSenkouSpanB() != 0) return j;
        return -1;
    }

    static double calcAvgTurnover(List<StockCandle> c, int days) {
        int start = Math.max(0, c.size() - days);
        double sum = 0; int n = 0;
        for (int j = start; j < c.size(); j++) { sum += c.get(j).getTurnover(); n++; }
        return n > 0 ? sum / n : 0;
    }
}
