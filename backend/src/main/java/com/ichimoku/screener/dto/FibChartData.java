package com.ichimoku.screener.dto;

import java.util.List;

public class FibChartData {

    private String symbol;
    private List<CandlePoint> candles;
    private String direction;
    private String impulseStartDate;
    private String impulseEndDate;
    private double impulseStartPrice;
    private double impulseEndPrice;
    private double fib236;
    private double fib382;
    private double fib500;
    private double fib618;
    private double fib786;
    private double stopLoss;
    private boolean inPlay;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public List<CandlePoint> getCandles() { return candles; }
    public void setCandles(List<CandlePoint> candles) { this.candles = candles; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getImpulseStartDate() { return impulseStartDate; }
    public void setImpulseStartDate(String impulseStartDate) { this.impulseStartDate = impulseStartDate; }
    public String getImpulseEndDate() { return impulseEndDate; }
    public void setImpulseEndDate(String impulseEndDate) { this.impulseEndDate = impulseEndDate; }
    public double getImpulseStartPrice() { return impulseStartPrice; }
    public void setImpulseStartPrice(double impulseStartPrice) { this.impulseStartPrice = impulseStartPrice; }
    public double getImpulseEndPrice() { return impulseEndPrice; }
    public void setImpulseEndPrice(double impulseEndPrice) { this.impulseEndPrice = impulseEndPrice; }
    public double getFib236() { return fib236; }
    public void setFib236(double fib236) { this.fib236 = fib236; }
    public double getFib382() { return fib382; }
    public void setFib382(double fib382) { this.fib382 = fib382; }
    public double getFib500() { return fib500; }
    public void setFib500(double fib500) { this.fib500 = fib500; }
    public double getFib618() { return fib618; }
    public void setFib618(double fib618) { this.fib618 = fib618; }
    public double getFib786() { return fib786; }
    public void setFib786(double fib786) { this.fib786 = fib786; }
    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    public boolean isInPlay() { return inPlay; }
    public void setInPlay(boolean inPlay) { this.inPlay = inPlay; }

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
}
