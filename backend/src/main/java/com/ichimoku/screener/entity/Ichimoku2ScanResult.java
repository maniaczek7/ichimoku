package com.ichimoku.screener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ichimoku2_scan_result")
public class Ichimoku2ScanResult {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "scan_run_id", nullable = false)
    private Ichimoku2ScanRun scanRun;

    private String symbol, market, direction, token;
    private int columnNumber, cloudDays, score;
    private double lastClose;
    private LocalDateTime firstSeenAt;

    public Ichimoku2ScanResult() {}
    public Long getId() { return id; }
    public Ichimoku2ScanRun getScanRun() { return scanRun; }
    public void setScanRun(Ichimoku2ScanRun v) { scanRun = v; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String v) { symbol = v; }
    public String getMarket() { return market; }
    public void setMarket(String v) { market = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { direction = v; }
    public String getToken() { return token; }
    public void setToken(String v) { token = v; }
    public int getColumnNumber() { return columnNumber; }
    public void setColumnNumber(int v) { columnNumber = v; }
    public int getCloudDays() { return cloudDays; }
    public void setCloudDays(int v) { cloudDays = v; }
    public int getScore() { return score; }
    public void setScore(int v) { score = v; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double v) { lastClose = v; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime v) { firstSeenAt = v; }
}
