package com.ichimoku.screener.dto;

public class Ichimoku2TradeRecord {
    private String symbol, market, direction, entryDate, exitDate, exitReason, initialSlType, slMethod, stooqUrl;
    private double entryPrice, initialSl, exitPrice, finalSl, returnPct, rMultiple;
    private int holdingDays, slTrailCount;
    private boolean profitViaSl;

    public String getSymbol() { return symbol; }
    public void setSymbol(String v) { symbol = v; }
    public String getMarket() { return market; }
    public void setMarket(String v) { market = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { direction = v; }
    public String getEntryDate() { return entryDate; }
    public void setEntryDate(String v) { entryDate = v; }
    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double v) { entryPrice = v; }
    public double getInitialSl() { return initialSl; }
    public void setInitialSl(double v) { initialSl = v; }
    public String getInitialSlType() { return initialSlType; }
    public void setInitialSlType(String v) { initialSlType = v; }
    public String getSlMethod() { return slMethod; }
    public void setSlMethod(String v) { slMethod = v; }
    public String getExitDate() { return exitDate; }
    public void setExitDate(String v) { exitDate = v; }
    public double getExitPrice() { return exitPrice; }
    public void setExitPrice(double v) { exitPrice = v; }
    public double getFinalSl() { return finalSl; }
    public void setFinalSl(double v) { finalSl = v; }
    public String getExitReason() { return exitReason; }
    public void setExitReason(String v) { exitReason = v; }
    public int getHoldingDays() { return holdingDays; }
    public void setHoldingDays(int v) { holdingDays = v; }
    public double getReturnPct() { return returnPct; }
    public void setReturnPct(double v) { returnPct = v; }
    public double getRMultiple() { return rMultiple; }
    public void setRMultiple(double v) { rMultiple = v; }
    public int getSlTrailCount() { return slTrailCount; }
    public void setSlTrailCount(int v) { slTrailCount = v; }
    public boolean isProfitViaSl() { return profitViaSl; }
    public void setProfitViaSl(boolean v) { profitViaSl = v; }
    public String getStooqUrl() { return stooqUrl; }
    public void setStooqUrl(String v) { stooqUrl = v; }
}
