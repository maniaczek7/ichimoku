package com.ichimoku.screener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ichimoku2_trash")
public class Ichimoku2TrashEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol, market, direction, token, dropReason;
    private int previousColumn, score, cloudDays;
    private double lastClose;
    private LocalDateTime firstSeenAt, lastSeenAt, droppedAt;
    private boolean dismissed;

    public Ichimoku2TrashEntry() {}
    public Long getId() { return id; }
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
    public boolean isDismissed() { return dismissed; }
    public void setDismissed(boolean v) { dismissed = v; }
}
