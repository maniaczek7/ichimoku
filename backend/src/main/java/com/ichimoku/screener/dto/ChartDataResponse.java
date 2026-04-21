package com.ichimoku.screener.dto;

import java.util.List;

public class ChartDataResponse {
    private String symbol;
    private List<CandlePoint> candles;
    private List<IchimokuPoint> ichimoku;
    private List<CloudProjection> futureCloud;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public List<CandlePoint> getCandles() { return candles; }
    public void setCandles(List<CandlePoint> candles) { this.candles = candles; }
    public List<IchimokuPoint> getIchimoku() { return ichimoku; }
    public void setIchimoku(List<IchimokuPoint> ichimoku) { this.ichimoku = ichimoku; }
    public List<CloudProjection> getFutureCloud() { return futureCloud; }
    public void setFutureCloud(List<CloudProjection> futureCloud) { this.futureCloud = futureCloud; }

    public static class CandlePoint {
        private String time;
        private double open, high, low, close;

        public CandlePoint(String time, double open, double high, double low, double close) {
            this.time = time; this.open = open; this.high = high; this.low = low; this.close = close;
        }

        public String getTime() { return time; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
    }

    public static class IchimokuPoint {
        private String time;
        private double tenkan, kijun, spanA, spanB;
        private Double chikou;

        public IchimokuPoint(String time, double tenkan, double kijun, double spanA, double spanB, Double chikou) {
            this.time = time; this.tenkan = tenkan; this.kijun = kijun;
            this.spanA = spanA; this.spanB = spanB; this.chikou = chikou;
        }

        public String getTime() { return time; }
        public double getTenkan() { return tenkan; }
        public double getKijun() { return kijun; }
        public double getSpanA() { return spanA; }
        public double getSpanB() { return spanB; }
        public Double getChikou() { return chikou; }
    }

    public static class CloudProjection {
        private String time;
        private double spanA, spanB;

        public CloudProjection(String time, double spanA, double spanB) {
            this.time = time; this.spanA = spanA; this.spanB = spanB;
        }

        public String getTime() { return time; }
        public double getSpanA() { return spanA; }
        public double getSpanB() { return spanB; }
    }
}
