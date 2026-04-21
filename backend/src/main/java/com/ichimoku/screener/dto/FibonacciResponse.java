package com.ichimoku.screener.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FibonacciResponse {

    private List<FibonacciEntry> column1 = new ArrayList<>();
    private List<FibonacciEntry> column2 = new ArrayList<>();
    private List<FibonacciEntry> column3 = new ArrayList<>();
    private List<AnalysisLogEntry> log;
    private String dataSource;
    private boolean hasPreviousScan;
    private LocalDateTime previousScanTime;
    private LocalDateTime scanTime;
    private List<FibDroppedEntry> dropped = new ArrayList<>();

    public List<FibonacciEntry> getColumn1() { return column1; }
    public void setColumn1(List<FibonacciEntry> column1) { this.column1 = column1; }
    public List<FibonacciEntry> getColumn2() { return column2; }
    public void setColumn2(List<FibonacciEntry> column2) { this.column2 = column2; }
    public List<FibonacciEntry> getColumn3() { return column3; }
    public void setColumn3(List<FibonacciEntry> column3) { this.column3 = column3; }
    public List<AnalysisLogEntry> getLog() { return log; }
    public void setLog(List<AnalysisLogEntry> log) { this.log = log; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public boolean isHasPreviousScan() { return hasPreviousScan; }
    public void setHasPreviousScan(boolean hasPreviousScan) { this.hasPreviousScan = hasPreviousScan; }
    public LocalDateTime getPreviousScanTime() { return previousScanTime; }
    public void setPreviousScanTime(LocalDateTime previousScanTime) { this.previousScanTime = previousScanTime; }
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime scanTime) { this.scanTime = scanTime; }
    public List<FibDroppedEntry> getDropped() { return dropped; }
    public void setDropped(List<FibDroppedEntry> dropped) { this.dropped = dropped; }
}
