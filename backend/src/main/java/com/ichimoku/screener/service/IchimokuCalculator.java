package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.IchimokuValues;
import com.ichimoku.screener.dto.StockCandle;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class IchimokuCalculator {

    private static final int TENKAN_PERIOD = 9;
    private static final int KIJUN_PERIOD = 26;
    private static final int SENKOU_B_PERIOD = 52;
    private static final int DISPLACEMENT = 26;

    public List<IchimokuValues> calculate(List<StockCandle> candles) {
        int size = candles.size();
        List<IchimokuValues> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            result.add(new IchimokuValues());
        }

        double[] tenkan = new double[size];
        double[] kijun = new double[size];
        double[] rawSpanA = new double[size];
        double[] rawSpanB = new double[size];

        for (int i = 0; i < size; i++) {
            if (i >= TENKAN_PERIOD - 1) {
                tenkan[i] = midpoint(candles, i - TENKAN_PERIOD + 1, i);
            }
            if (i >= KIJUN_PERIOD - 1) {
                kijun[i] = midpoint(candles, i - KIJUN_PERIOD + 1, i);
            }
            if (i >= KIJUN_PERIOD - 1) {
                rawSpanA[i] = (tenkan[i] + kijun[i]) / 2.0;
            }
            if (i >= SENKOU_B_PERIOD - 1) {
                rawSpanB[i] = midpoint(candles, i - SENKOU_B_PERIOD + 1, i);
            }

            result.get(i).setTenkanSen(tenkan[i]);
            result.get(i).setKijunSen(kijun[i]);
        }

        for (int i = 0; i < size; i++) {
            int sourceIdx = i - DISPLACEMENT;
            if (sourceIdx >= KIJUN_PERIOD - 1) {
                result.get(i).setSenkouSpanA(rawSpanA[sourceIdx]);
            }
            if (sourceIdx >= SENKOU_B_PERIOD - 1) {
                result.get(i).setSenkouSpanB(rawSpanB[sourceIdx]);
            }
        }

        for (int i = 0; i < size; i++) {
            int futureIdx = i + DISPLACEMENT;
            if (futureIdx < size) {
                result.get(i).setChikouSpan(candles.get(futureIdx).getClose());
            }
        }

        return result;
    }

    public double[][] getFutureCloud(List<StockCandle> candles) {
        int size = candles.size();
        double[][] future = new double[DISPLACEMENT][2];

        for (int offset = 0; offset < DISPLACEMENT; offset++) {
            int sourceIdx = size - DISPLACEMENT + offset;
            if (sourceIdx < 0) continue;

            double t = sourceIdx >= TENKAN_PERIOD - 1 ? midpoint(candles, sourceIdx - TENKAN_PERIOD + 1, sourceIdx) : 0;
            double k = sourceIdx >= KIJUN_PERIOD - 1 ? midpoint(candles, sourceIdx - KIJUN_PERIOD + 1, sourceIdx) : 0;
            double spanA = (t + k) / 2.0;
            double spanB = sourceIdx >= SENKOU_B_PERIOD - 1 ? midpoint(candles, sourceIdx - SENKOU_B_PERIOD + 1, sourceIdx) : 0;

            future[offset][0] = spanA;
            future[offset][1] = spanB;
        }

        return future;
    }

    private double midpoint(List<StockCandle> candles, int from, int to) {
        double highest = Double.MIN_VALUE;
        double lowest = Double.MAX_VALUE;
        for (int i = from; i <= to; i++) {
            highest = Math.max(highest, candles.get(i).getHigh());
            lowest = Math.min(lowest, candles.get(i).getLow());
        }
        return (highest + lowest) / 2.0;
    }
}
