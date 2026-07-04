package com.ichimoku.screener.dto;

import java.time.LocalDateTime;

public class Ichimoku2TrashDto {
    private Long id;
    private String symbol, market, direction, token, dropReason;
    private int previousColumn, score, cloudDays;
    private double lastClose;
    private LocalDateTime firstSeenAt, lastSeenAt, droppedAt;

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String v) { symbol = v; }
    public String getMarket() { return market; }
    public void setMarket(String v) { market = v; }
    public String getDirection() { return direction; }
    public void setDirection(String v) { direction = v; }
    public String getToken() { return token; }
    public void setToken(String v) { token = v; }
    public int getPreviousColumn() { return previousColumn; }
    public void setPreviousColumn(int v) { previousColumn = v; }
    public int getScore() { return score; }
    public void setScore(int v) { score = v; }
    public int getCloudDays() { return cloudDays; }
    public void setCloudDays(int v) { cloudDays = v; }
    public double getLastClose() { return lastClose; }
    public void setLastClose(double v) { lastClose = v; }
    public String getDropReason() { return dropReason; }
    public void setDropReason(String v) { dropReason = v; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime v) { firstSeenAt = v; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(LocalDateTime v) { lastSeenAt = v; }
    public LocalDateTime getDroppedAt() { return droppedAt; }
    public void setDroppedAt(LocalDateTime v) { droppedAt = v; }
}
