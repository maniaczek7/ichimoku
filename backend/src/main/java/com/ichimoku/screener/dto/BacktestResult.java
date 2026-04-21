package com.ichimoku.screener.dto;

import java.util.ArrayList;
import java.util.List;

public class BacktestResult {
    private int totalTrades;
    private int wins;
    private int losses;
    private double winRatePct;
    private double avgReturnPct;
    private double avgWinPct;
    private double avgLossPct;
    private double maxDrawdownPct;
    private double profitFactor;
    private double avgRMultiple;
    private double avgHoldingDays;
    private double slHitRatePct;

    private List<TradeRecord> trades = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int totalTrades) { this.totalTrades = totalTrades; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
    public int getLosses() { return losses; }
    public void setLosses(int losses) { this.losses = losses; }
    public double getWinRatePct() { return winRatePct; }
    public void setWinRatePct(double winRatePct) { this.winRatePct = winRatePct; }
    public double getAvgReturnPct() { return avgReturnPct; }
    public void setAvgReturnPct(double avgReturnPct) { this.avgReturnPct = avgReturnPct; }
    public double getAvgWinPct() { return avgWinPct; }
    public void setAvgWinPct(double avgWinPct) { this.avgWinPct = avgWinPct; }
    public double getAvgLossPct() { return avgLossPct; }
    public void setAvgLossPct(double avgLossPct) { this.avgLossPct = avgLossPct; }
    public double getMaxDrawdownPct() { return maxDrawdownPct; }
    public void setMaxDrawdownPct(double maxDrawdownPct) { this.maxDrawdownPct = maxDrawdownPct; }
    public double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(double profitFactor) { this.profitFactor = profitFactor; }
    public double getAvgRMultiple() { return avgRMultiple; }
    public void setAvgRMultiple(double avgRMultiple) { this.avgRMultiple = avgRMultiple; }
    public double getAvgHoldingDays() { return avgHoldingDays; }
    public void setAvgHoldingDays(double avgHoldingDays) { this.avgHoldingDays = avgHoldingDays; }
    public double getSlHitRatePct() { return slHitRatePct; }
    public void setSlHitRatePct(double slHitRatePct) { this.slHitRatePct = slHitRatePct; }

    public List<TradeRecord> getTrades() { return trades; }
    public void setTrades(List<TradeRecord> trades) { this.trades = trades; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }
}
