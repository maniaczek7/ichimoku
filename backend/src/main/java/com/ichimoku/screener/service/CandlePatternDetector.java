package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.StockCandle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects top 8 candlestick reversal patterns (Thomas Bulkowski ranking).
 */
@Service
public class CandlePatternDetector {

    private static final String[] BULLISH_PATTERNS = {
            "Bullish Engulfing", "Bullish Harami", "Morning Star", "Hammer"
    };
    private static final String[] BEARISH_PATTERNS = {
            "Bearish Engulfing", "Bearish Harami", "Evening Star", "Shooting Star"
    };

    /**
     * Detect patterns relevant to the given impulse direction (for Fibonacci screener).
     * After a bullish impulse we look for bearish reversal patterns (correction ending),
     * after a bearish impulse we look for bullish reversal patterns.
     */
    public static List<String> detectAll(List<StockCandle> candles, String impulseDir) {
        List<String> found = new ArrayList<>();
        if (candles.size() < 3) return found;

        boolean expectBullReversal = "BEARISH".equals(impulseDir);

        if (expectBullReversal) {
            if (isBullishEngulfing(candles)) found.add("Bullish Engulfing");
            if (isBullishHarami(candles))    found.add("Bullish Harami");
            if (isMorningStar(candles))      found.add("Morning Star");
            if (isHammerCandle(candles))     found.add("Hammer");
        } else {
            if (isBearishEngulfing(candles)) found.add("Bearish Engulfing");
            if (isBearishHarami(candles))    found.add("Bearish Harami");
            if (isEveningStar(candles))      found.add("Evening Star");
            if (isShootingStarCandle(candles)) found.add("Shooting Star");
        }

        return found;
    }

    /**
     * Legacy API used by ScreenerService (Ichimoku).
     * Returns the first detected pattern name, or null.
     */
    public String detectPattern(List<StockCandle> candles) {
        if (candles == null || candles.size() < 3) return null;

        if (isBullishEngulfing(candles)) return "Bullish Engulfing";
        if (isBearishEngulfing(candles)) return "Bearish Engulfing";
        if (isBullishHarami(candles))    return "Bullish Harami";
        if (isBearishHarami(candles))    return "Bearish Harami";
        if (isMorningStar(candles))      return "Morning Star";
        if (isEveningStar(candles))      return "Evening Star";
        if (isHammerCandle(candles))     return "Hammer";
        if (isShootingStarCandle(candles)) return "Shooting Star";

        return null;
    }

    public boolean isBullishPattern(String pattern) {
        if (pattern == null) return false;
        for (String p : BULLISH_PATTERNS) {
            if (p.equals(pattern)) return true;
        }
        return false;
    }

    public boolean isBearishPattern(String pattern) {
        if (pattern == null) return false;
        for (String p : BEARISH_PATTERNS) {
            if (p.equals(pattern)) return true;
        }
        return false;
    }

    // ── 1. Bullish Engulfing ──
    static boolean isBullishEngulfing(List<StockCandle> c) {
        int n = c.size();
        StockCandle prev = c.get(n - 2);
        StockCandle curr = c.get(n - 1);
        if (!isBearish(prev) || !isBullish(curr)) return false;
        return curr.getOpen() <= prev.getClose() && curr.getClose() >= prev.getOpen()
                && body(curr) > body(prev) * 1.1;
    }

    // ── 2. Bearish Engulfing ──
    static boolean isBearishEngulfing(List<StockCandle> c) {
        int n = c.size();
        StockCandle prev = c.get(n - 2);
        StockCandle curr = c.get(n - 1);
        if (!isBullish(prev) || !isBearish(curr)) return false;
        return curr.getOpen() >= prev.getClose() && curr.getClose() <= prev.getOpen()
                && body(curr) > body(prev) * 1.1;
    }

    // ── 3. Bullish Harami ──
    static boolean isBullishHarami(List<StockCandle> c) {
        int n = c.size();
        StockCandle prev = c.get(n - 2);
        StockCandle curr = c.get(n - 1);
        if (!isBearish(prev) || !isBullish(curr)) return false;
        double pOpen = prev.getOpen(), pClose = prev.getClose();
        double cOpen = curr.getOpen(), cClose = curr.getClose();
        return cOpen >= pClose && cClose <= pOpen && body(curr) < body(prev) * 0.6;
    }

    // ── 4. Bearish Harami ──
    static boolean isBearishHarami(List<StockCandle> c) {
        int n = c.size();
        StockCandle prev = c.get(n - 2);
        StockCandle curr = c.get(n - 1);
        if (!isBullish(prev) || !isBearish(curr)) return false;
        double pOpen = prev.getOpen(), pClose = prev.getClose();
        double cOpen = curr.getOpen(), cClose = curr.getClose();
        return cOpen <= pClose && cClose >= pOpen && body(curr) < body(prev) * 0.6;
    }

    // ── 5. Morning Star (3-candle bullish reversal) ──
    static boolean isMorningStar(List<StockCandle> c) {
        int n = c.size();
        if (n < 3) return false;
        StockCandle first = c.get(n - 3);
        StockCandle mid = c.get(n - 2);
        StockCandle last = c.get(n - 1);
        if (!isBearish(first) || !isBullish(last)) return false;
        double midBody = body(mid);
        double firstBody = body(first);
        if (midBody > firstBody * 0.3) return false;
        double midMid = (mid.getOpen() + mid.getClose()) / 2;
        return midMid < first.getClose() && last.getClose() > (first.getOpen() + first.getClose()) / 2;
    }

    // ── 6. Evening Star (3-candle bearish reversal) ──
    static boolean isEveningStar(List<StockCandle> c) {
        int n = c.size();
        if (n < 3) return false;
        StockCandle first = c.get(n - 3);
        StockCandle mid = c.get(n - 2);
        StockCandle last = c.get(n - 1);
        if (!isBullish(first) || !isBearish(last)) return false;
        double midBody = body(mid);
        double firstBody = body(first);
        if (midBody > firstBody * 0.3) return false;
        double midMid = (mid.getOpen() + mid.getClose()) / 2;
        return midMid > first.getClose() && last.getClose() < (first.getOpen() + first.getClose()) / 2;
    }

    // ── 7. Hammer (single candle bullish reversal) ──
    static boolean isHammerCandle(List<StockCandle> c) {
        StockCandle candle = c.get(c.size() - 1);
        double b = body(candle);
        if (b <= 0) return false;
        double lowerShadow = Math.min(candle.getOpen(), candle.getClose()) - candle.getLow();
        double upperShadow = candle.getHigh() - Math.max(candle.getOpen(), candle.getClose());
        return lowerShadow >= b * 2 && upperShadow <= b * 0.3;
    }

    // ── 8. Shooting Star (single candle bearish reversal) ──
    static boolean isShootingStarCandle(List<StockCandle> c) {
        StockCandle candle = c.get(c.size() - 1);
        double b = body(candle);
        if (b <= 0) return false;
        double upperShadow = candle.getHigh() - Math.max(candle.getOpen(), candle.getClose());
        double lowerShadow = Math.min(candle.getOpen(), candle.getClose()) - candle.getLow();
        return upperShadow >= b * 2 && lowerShadow <= b * 0.3;
    }

    // ── helpers ──
    private static double body(StockCandle c) {
        return Math.abs(c.getClose() - c.getOpen());
    }

    private static boolean isBullish(StockCandle c) {
        return c.getClose() > c.getOpen();
    }

    private static boolean isBearish(StockCandle c) {
        return c.getClose() < c.getOpen();
    }
}
