package com.ichimoku.screener.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TrojpolowkaResponse {
    private List<TrojpolowkaEntry> column1 = new ArrayList<>();
    private List<TrojpolowkaEntry> column2 = new ArrayList<>();
    private List<TrojpolowkaEntry> column3 = new ArrayList<>();
    private List<AnalysisLogEntry> log = new ArrayList<>();
    private String dataSource;

    private List<DroppedEntry> dropped = new ArrayList<>();
    private boolean hasPreviousScan;
    private LocalDateTime previousScanTime;
    private LocalDateTime scanTime;

    public List<TrojpolowkaEntry> getColumn1() { return column1; }
    public void setColumn1(List<TrojpolowkaEntry> column1) { this.column1 = column1; }
    public List<TrojpolowkaEntry> getColumn2() { return column2; }
    public void setColumn2(List<TrojpolowkaEntry> column2) { this.column2 = column2; }
    public List<TrojpolowkaEntry> getColumn3() { return column3; }
    public void setColumn3(List<TrojpolowkaEntry> column3) { this.column3 = column3; }
    public List<AnalysisLogEntry> getLog() { return log; }
    public void setLog(List<AnalysisLogEntry> log) { this.log = log; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }

    public List<DroppedEntry> getDropped() { return dropped; }
    public void setDropped(List<DroppedEntry> dropped) { this.dropped = dropped; }
    public boolean isHasPreviousScan() { return hasPreviousScan; }
    public void setHasPreviousScan(boolean hasPreviousScan) { this.hasPreviousScan = hasPreviousScan; }
    public LocalDateTime getPreviousScanTime() { return previousScanTime; }
    public void setPreviousScanTime(LocalDateTime previousScanTime) { this.previousScanTime = previousScanTime; }
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
}
