package com.ichimoku.screener.dto;

import java.util.ArrayList;
import java.util.List;

public class Ichimoku2BacktestResult {
    private int totalTrades, wins, losses, profitViaSlCount;
    private double winRatePct, avgReturnPct, avgWinPct, avgLossPct, maxDrawdownPct, profitFactor, avgRMultiple, avgHoldingDays, slHitRatePct;
    private String slMethod;
    private List<Ichimoku2TradeRecord> trades = new ArrayList<>();
    private List<String> errors = new ArrayList<>();

    public int getTotalTrades() { return totalTrades; }
    public void setTotalTrades(int v) { totalTrades = v; }
    public int getWins() { return wins; }
    public void setWins(int v) { wins = v; }
    public int getLosses() { return losses; }
    public void setLosses(int v) { losses = v; }
    public double getWinRatePct() { return winRatePct; }
    public void setWinRatePct(double v) { winRatePct = v; }
    public double getAvgReturnPct() { return avgReturnPct; }
    public void setAvgReturnPct(double v) { avgReturnPct = v; }
    public double getAvgWinPct() { return avgWinPct; }
    public void setAvgWinPct(double v) { avgWinPct = v; }
    public double getAvgLossPct() { return avgLossPct; }
    public void setAvgLossPct(double v) { avgLossPct = v; }
    public double getMaxDrawdownPct() { return maxDrawdownPct; }
    public void setMaxDrawdownPct(double v) { maxDrawdownPct = v; }
    public double getProfitFactor() { return profitFactor; }
    public void setProfitFactor(double v) { profitFactor = v; }
    public double getAvgRMultiple() { return avgRMultiple; }
    public void setAvgRMultiple(double v) { avgRMultiple = v; }
    public double getAvgHoldingDays() { return avgHoldingDays; }
    public void setAvgHoldingDays(double v) { avgHoldingDays = v; }
    public double getSlHitRatePct() { return slHitRatePct; }
    public void setSlHitRatePct(double v) { slHitRatePct = v; }
    public int getProfitViaSlCount() { return profitViaSlCount; }
    public void setProfitViaSlCount(int v) { profitViaSlCount = v; }
    public String getSlMethod() { return slMethod; }
    public void setSlMethod(String v) { slMethod = v; }
    public List<Ichimoku2TradeRecord> getTrades() { return trades; }
    public List<String> getErrors() { return errors; }
}
