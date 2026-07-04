package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.*;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.*;

@Service
public class Ichimoku2BacktestService {
    private static final int FOUR_MONTHS = 84, HISTORY = 2500, MIN_BARS = 100, COOLDOWN = 20;
    private static final double HUGE_CANDLE_ATR_MULT = 2.0;

    private final MarketDataService marketData;
    private final IchimokuCalculator calc;

    public Ichimoku2BacktestService(MarketDataService marketData, IchimokuCalculator calc) {
        this.marketData = marketData;
        this.calc = calc;
    }

    public Ichimoku2BacktestResult run(boolean gpw, boolean us, List<String> forex, int usTopN, String slMethod,
                                        java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        marketData.resetForNewScan();
        String method = "CONFIRMED_SHADOWS".equals(slMethod) ? "CONFIRMED_SHADOWS" : "CONFIRMED_LOWS";
        List<String[]> syms = new ArrayList<>();
        if (gpw) ScreenerService.GPW_SYMBOLS.forEach(s -> syms.add(new String[]{s, "GPW"}));
        if (us) ScreenerService.US_SYMBOLS.stream().limit(usTopN).forEach(s -> syms.add(new String[]{s, "US"}));
        if (forex != null) forex.forEach(s -> syms.add(new String[]{s, "FOREX"}));

        Ichimoku2BacktestResult result = new Ichimoku2BacktestResult();
        result.setSlMethod(method);
        ExecutorService ex = Executors.newFixedThreadPool(6);
        List<Future<List<Ichimoku2TradeRecord>>> futures = new ArrayList<>();
        for (String[] sym : syms) futures.add(ex.submit(() -> backtestSymbol(sym[0], sym[1], method, dateFrom, dateTo)));
        ex.shutdown();
        for (int i = 0; i < futures.size(); i++) {
            try { result.getTrades().addAll(futures.get(i).get(120, TimeUnit.SECONDS)); }
            catch (Exception e) { result.getErrors().add(syms.get(i)[0] + ": " + e.getMessage()); }
        }
        result.getTrades().sort(Comparator.comparing(Ichimoku2TradeRecord::getEntryDate));
        computeAggregates(result);
        return result;
    }

    private List<Ichimoku2TradeRecord> backtestSymbol(String symbol, String market, String method,
                                                       java.time.LocalDate dateFrom, java.time.LocalDate dateTo) {
        List<StockCandle> candles = marketData.downloadHistory(symbol, HISTORY);
        if (candles.size() < MIN_BARS + FOUR_MONTHS) return List.of();
        List<IchimokuValues> ichi = calc.calculate(candles);
        List<Ichimoku2TradeRecord> trades = new ArrayList<>();
        int cooldown = 0;
        for (int i = MIN_BARS + FOUR_MONTHS; i < candles.size(); i++) {
            if (i <= cooldown) continue;
            var barDate = candles.get(i).getDate();
            if (dateFrom != null && barDate.isBefore(dateFrom)) continue;
            if (dateTo != null && barDate.isAfter(dateTo)) continue;
            String dir = detectEntry(candles, ichi, i);
            if (dir == null) continue;
            Ichimoku2TradeRecord t = simulate(candles, ichi, i, dir, method);
            if (t == null) continue;
            t.setSymbol(symbol); t.setMarket(market);
            trades.add(t);
            int exitIdx = findIdx(candles, t.getExitDate(), i);
            cooldown = exitIdx + COOLDOWN;
            i = exitIdx;
        }
        return trades;
    }

    private String detectEntry(List<StockCandle> c, List<IchimokuValues> i, int idx) {
        double close = c.get(idx).getClose(), prev = c.get(idx - 1).getClose();
        double top = i.get(idx).getCloudTop(), bot = i.get(idx).getCloudBottom();
        double pTop = i.get(idx - 1).getCloudTop(), pBot = i.get(idx - 1).getCloudBottom();
        if (top == 0 || bot == 0) return null;
        if (close > top && prev <= pTop) {
            if (Ichimoku2Service.countQualified(c, i, idx - 1, 0, true) >= FOUR_MONTHS) return "LONG";
        }
        if (close < bot && prev >= pBot) {
            if (Ichimoku2Service.countQualified(c, i, idx - 1, 0, false) >= FOUR_MONTHS) return "SHORT";
        }
        return null;
    }

    private Ichimoku2TradeRecord simulate(List<StockCandle> c, List<IchimokuValues> i, int entryIdx, String dir, String method) {
        boolean long_ = "LONG".equals(dir);
        double entry = c.get(entryIdx).getClose();
        double top = i.get(entryIdx).getCloudTop(), bot = i.get(entryIdx).getCloudBottom();
        StockCandle bar = c.get(entryIdx);
        boolean pierce = long_ ? bar.getLow() < bot && bar.getHigh() > top : bar.getHigh() > top && bar.getLow() < bot;
        double sl = pierce ? (long_ ? bar.getLow() : bar.getHigh()) : (long_ ? bot : top);
        String slType = pierce ? "BREAKOUT_CANDLE" : "CLOUD_EDGE";
        if (sl == 0) return null;
        double risk = Math.abs(entry - sl);
        if (risk < entry * 0.001) return null;

        Ichimoku2TradeRecord t = new Ichimoku2TradeRecord();
        t.setDirection(dir); t.setEntryDate(c.get(entryIdx).getDate().toString()); t.setEntryPrice(entry);
        t.setInitialSl(sl); t.setInitialSlType(slType); t.setSlMethod(method);
        int trails = 0, lastConfirm = entryIdx;
        double[] atr = calcAtr(c, 14);

        for (int j = entryIdx + 1; j < c.size(); j++) {
            StockCandle b = c.get(j);
            if (long_ && b.getLow() <= sl) return closeTrade(t, b, sl, entry, risk, true, "SL_HIT", j - entryIdx, trails);
            if (!long_ && b.getHigh() >= sl) return closeTrade(t, b, sl, entry, risk, false, "SL_HIT", j - entryIdx, trails);

            double newSl = "CONFIRMED_SHADOWS".equals(method)
                    ? trailShadows(c, j, entryIdx, long_, atr[j])
                    : trailLows(c, j, entryIdx, lastConfirm, long_);
            if (newSl > 0 && (long_ ? newSl > sl : newSl < sl)) {
                sl = newSl; trails++; lastConfirm = j;
            }
        }
        int last = c.size() - 1;
        return closeTrade(t, c.get(last), c.get(last).getClose(), entry, risk, long_, "DATA_END", last - entryIdx, trails);
    }

    private double trailLows(List<StockCandle> c, int idx, int entry, int lastConfirm, boolean long_) {
        if (!long_) return trailLowsShort(c, idx, entry, lastConfirm);
        double close = c.get(idx).getClose();
        for (int j = entry; j < idx; j++) if (close <= c.get(j).getClose()) return -1;
        double low = c.get(idx).getLow();
        for (int j = lastConfirm; j < idx; j++) low = Math.min(low, c.get(j).getLow());
        return low;
    }

    private double trailLowsShort(List<StockCandle> c, int idx, int entry, int lastConfirm) {
        double close = c.get(idx).getClose();
        for (int j = entry; j < idx; j++) if (close >= c.get(j).getClose()) return -1;
        double high = c.get(idx).getHigh();
        for (int j = lastConfirm; j < idx; j++) high = Math.max(high, c.get(j).getHigh());
        return high;
    }

    private double trailShadows(List<StockCandle> c, int idx, int entry, boolean long_, double atr) {
        if (idx < 1 || atr <= 0) return -1;
        StockCandle cur = c.get(idx), prev = c.get(idx - 1);
        double body = Math.abs(cur.getClose() - cur.getOpen());
        if (body < atr * HUGE_CANDLE_ATR_MULT) return -1;
        if (long_ && cur.getClose() <= cur.getOpen()) return -1;
        if (!long_ && cur.getClose() >= cur.getOpen()) return -1;
        return long_ ? prev.getLow() : prev.getHigh();
    }

    private double[] calcAtr(List<StockCandle> c, int period) {
        double[] atr = new double[c.size()];
        for (int i = 1; i < c.size(); i++) {
            double tr = Math.max(c.get(i).getHigh() - c.get(i).getLow(),
                    Math.max(Math.abs(c.get(i).getHigh() - c.get(i - 1).getClose()), Math.abs(c.get(i).getLow() - c.get(i - 1).getClose())));
            if (i < period) atr[i] = tr;
            else atr[i] = (atr[i - 1] * (period - 1) + tr) / period;
        }
        return atr;
    }

    private Ichimoku2TradeRecord closeTrade(Ichimoku2TradeRecord t, StockCandle b, double exit, double entry, double risk, boolean long_, String reason, int days, int trails) {
        t.setExitDate(b.getDate().toString()); t.setExitPrice(exit); t.setFinalSl(exit);
        t.setExitReason(reason); t.setHoldingDays(days); t.setSlTrailCount(trails);
        double pnl = long_ ? exit - entry : entry - exit;
        t.setReturnPct(pnl / entry * 100);
        t.setRMultiple(risk != 0 ? pnl / risk : 0);
        t.setProfitViaSl("SL_HIT".equals(reason) && pnl > 0);
        t.setStooqUrl("https://stooq.com/q/?s=" + t.getSymbol().toLowerCase() + "&i=d");
        return t;
    }

    private int findIdx(List<StockCandle> c, String date, int from) {
        for (int i = from; i < c.size(); i++) if (c.get(i).getDate().toString().equals(date)) return i;
        return c.size() - 1;
    }

    private void computeAggregates(Ichimoku2BacktestResult r) {
        var trades = r.getTrades();
        r.setTotalTrades(trades.size());
        if (trades.isEmpty()) return;
        int wins = 0, slHits = 0, profitSl = 0;
        double sumRet = 0, sumR = 0, sumHold = 0, grossP = 0, grossL = 0, maxDd = 0;
        for (var t : trades) {
            sumRet += t.getReturnPct(); sumR += t.getRMultiple(); sumHold += t.getHoldingDays();
            if (t.getReturnPct() > 0) { wins++; grossP += t.getReturnPct(); }
            else grossL += Math.abs(t.getReturnPct());
            if ("SL_HIT".equals(t.getExitReason())) slHits++;
            if (t.isProfitViaSl()) profitSl++;
            maxDd = Math.min(maxDd, t.getReturnPct());
        }
        int n = trades.size();
        r.setWins(wins); r.setLosses(n - wins);
        r.setWinRatePct(wins * 100.0 / n); r.setAvgReturnPct(sumRet / n);
        r.setAvgRMultiple(sumR / n); r.setAvgHoldingDays(sumHold / n);
        r.setMaxDrawdownPct(maxDd); r.setSlHitRatePct(slHits * 100.0 / n);
        r.setProfitViaSlCount(profitSl);
        r.setProfitFactor(grossL > 0 ? grossP / grossL : grossP > 0 ? 999 : 0);
    }
}
