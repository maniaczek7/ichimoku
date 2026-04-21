package com.ichimoku.screener.dto;

public class AnalysisLogEntry {
    private String symbol;
    private String market;
    private String status;
    private String detail;

    public AnalysisLogEntry() {}

    public AnalysisLogEntry(String symbol, String market, String status, String detail) {
        this.symbol = symbol;
        this.market = market;
        this.status = status;
        this.detail = detail;
    }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getMarket() { return market; }
    public void setMarket(String market) { this.market = market; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
