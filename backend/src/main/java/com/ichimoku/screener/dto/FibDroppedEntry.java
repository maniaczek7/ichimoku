package com.ichimoku.screener.dto;

public class FibDroppedEntry {
    private String symbol;
    private String market;
    private String direction;
    private int previousColumn;
    private double lastClose;

    public FibDroppedEntry() {}

    public FibDroppedEntry(String symbol, String market, String direction, int previousColumn, double lastClose) {
        this.symbol = symbol;
        this.market = market;
        this.direction = direction;
        this.previousColumn = previousColumn;
        this.lastClose = lastClose;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public int getPreviousColumn() { return previousColumn; }
    public void setPreviousColumn(int previousColumn) { this.previousColumn = previousColumn; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double lastClose) { this.lastClose = lastClose; }
}
