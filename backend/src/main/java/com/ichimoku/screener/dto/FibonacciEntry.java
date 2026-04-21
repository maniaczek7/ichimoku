package com.ichimoku.screener.dto;

import java.util.ArrayList;
import java.util.List;

public class FibonacciEntry {

    private String symbol;
    private String market;
    private String direction;           // BULLISH (impulse up) or BEARISH (impulse down)
    private double lastClose;

    private String impulseStartDate;
    private String impulseEndDate;
    private double impulseStartPrice;   // low for bullish, high for bearish
    private double impulseEndPrice;     // high for bullish, low for bearish
    private double impulseHeight;       // absolute height
    private double impulsePct;          // impulse as % of start price
    private int impulseDays;

    private double fib236;
    private double fib382;
    private double fib500;
    private double fib618;
    private double fib786;

    private double retracementPct;      // how much has retraced (0-100% of impulse)
    private double retracementFromPeak; // % drop/rise from impulse end
    private String nearestFibLevel;     // e.g. "38.2%", "50%"
    private double distToFib50Pct;      // distance to fib 50% as % of price

    private int column;                 // 1=Impuls, 2=Zawraca, 3=Fib50
    private List<String> signals = new ArrayList<>();
    private List<String> patterns = new ArrayList<>();
    private boolean fibBounce;
    private boolean inPlay;             // setup active (pattern at 61.8% and SL not hit)
    private double stopLoss;            // low/high of pattern candles at fib bounce
    private String bouncePatternDate;   // date when the pattern occurred
    private String changeStatus;        // NEW, MOVED_UP, MOVED_DOWN, UNCHANGED
    private int previousColumn;

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double lastClose) { this.lastClose = lastClose; }

    public String getImpulseStartDate() { return impulseStartDate; }
    public void setImpulseStartDate(String impulseStartDate) { this.impulseStartDate = impulseStartDate; }
    public String getImpulseEndDate() { return impulseEndDate; }
    public void setImpulseEndDate(String impulseEndDate) { this.impulseEndDate = impulseEndDate; }
    public double getImpulseStartPrice() { return impulseStartPrice; }
    public void setImpulseStartPrice(double impulseStartPrice) { this.impulseStartPrice = impulseStartPrice; }
    public double getImpulseEndPrice() { return impulseEndPrice; }
    public void setImpulseEndPrice(double impulseEndPrice) { this.impulseEndPrice = impulseEndPrice; }
    public double getImpulseHeight() { return impulseHeight; }
    public void setImpulseHeight(double impulseHeight) { this.impulseHeight = impulseHeight; }
    public double getImpulsePct() { return impulsePct; }
    public void setImpulsePct(double impulsePct) { this.impulsePct = impulsePct; }
    public int getImpulseDays() { return impulseDays; }
    public void setImpulseDays(int impulseDays) { this.impulseDays = impulseDays; }

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

    public double getRetracementPct() { return retracementPct; }
    public void setRetracementPct(double retracementPct) { this.retracementPct = retracementPct; }
    public double getRetracementFromPeak() { return retracementFromPeak; }
    public void setRetracementFromPeak(double retracementFromPeak) { this.retracementFromPeak = retracementFromPeak; }
    public String getNearestFibLevel() { return nearestFibLevel; }
    public void setNearestFibLevel(String nearestFibLevel) { this.nearestFibLevel = nearestFibLevel; }
    public double getDistToFib50Pct() { return distToFib50Pct; }
    public void setDistToFib50Pct(double distToFib50Pct) { this.distToFib50Pct = distToFib50Pct; }

    public int getColumn() { return column; }
    public void setColumn(int column) { this.column = column; }
    public List<String> getSignals() { return signals; }
    public void setSignals(List<String> signals) { this.signals = signals; }
    public List<String> getPatterns() { return patterns; }
    public void setPatterns(List<String> patterns) { this.patterns = patterns; }
    public boolean isFibBounce() { return fibBounce; }
    public void setFibBounce(boolean fibBounce) { this.fibBounce = fibBounce; }
    public boolean isInPlay() { return inPlay; }
    public void setInPlay(boolean inPlay) { this.inPlay = inPlay; }
    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    public String getBouncePatternDate() { return bouncePatternDate; }
    public void setBouncePatternDate(String bouncePatternDate) { this.bouncePatternDate = bouncePatternDate; }
    public String getChangeStatus() { return changeStatus; }
    public void setChangeStatus(String changeStatus) { this.changeStatus = changeStatus; }
    public int getPreviousColumn() { return previousColumn; }
    public void setPreviousColumn(int previousColumn) { this.previousColumn = previousColumn; }
}
