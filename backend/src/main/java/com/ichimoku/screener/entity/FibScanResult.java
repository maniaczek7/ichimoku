package com.ichimoku.screener.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "fib_scan_result")
public class FibScanResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_run_id", nullable = false)
    private FibScanRun scanRun;

    private String symbol;
    private String market;
    private String direction;
    private int columnNumber;
    private double lastClose;

    private String impulseStartDate;
    private String impulseEndDate;
    private double impulseStartPrice;
    private double impulseEndPrice;
    private double impulseHeight;
    private double impulsePct;
    private int impulseDays;

    private double fib236;
    private double fib382;
    private double fib500;
    private double fib618;
    private double fib786;

    private double retracementPct;
    private double retracementFromPeak;
    private String nearestFibLevel;
    private double distToFib50Pct;

    @Column(columnDefinition = "boolean default false")
    private boolean fibBounce;

    @Column(columnDefinition = "boolean default false")
    private boolean inPlay;

    @Column(columnDefinition = "double default 0")
    private double stopLoss;

    private String bouncePatternDate;

    @Column(length = 500)
    private String patternsJson;

    @Column(length = 2000)
    private String signalsJson;

    public FibScanResult() {}

    public Long getId() { return id; }
    public FibScanRun getScanRun() { return scanRun; }
    public void setScanRun(FibScanRun scanRun) { this.scanRun = scanRun; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public int getColumnNumber() { return columnNumber; }
    public void setColumnNumber(int columnNumber) { this.columnNumber = columnNumber; }
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
    public boolean isFibBounce() { return fibBounce; }
    public void setFibBounce(boolean fibBounce) { this.fibBounce = fibBounce; }
    public boolean isInPlay() { return inPlay; }
    public void setInPlay(boolean inPlay) { this.inPlay = inPlay; }
    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    public String getBouncePatternDate() { return bouncePatternDate; }
    public void setBouncePatternDate(String bouncePatternDate) { this.bouncePatternDate = bouncePatternDate; }
    public String getPatternsJson() { return patternsJson; }
    public void setPatternsJson(String patternsJson) { this.patternsJson = patternsJson; }
    public String getSignalsJson() { return signalsJson; }
    public void setSignalsJson(String signalsJson) { this.signalsJson = signalsJson; }
}
