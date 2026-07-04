package com.ichimoku.screener.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Ichimoku2Response {
    private List<Ichimoku2Entry> column1 = new ArrayList<>(), column2 = new ArrayList<>(), column3 = new ArrayList<>();
    private List<Ichimoku2TrashDto> trashColumn1 = new ArrayList<>(), trashColumn2 = new ArrayList<>(), trashColumn3 = new ArrayList<>();
    private List<AnalysisLogEntry> log = new ArrayList<>();
    private boolean hasPreviousScan;
    private LocalDateTime previousScanTime, scanTime;
    private String dataSource;

    public List<Ichimoku2Entry> getColumn1() { return column1; }
    public List<Ichimoku2Entry> getColumn2() { return column2; }
    public List<Ichimoku2Entry> getColumn3() { return column3; }
    public List<Ichimoku2TrashDto> getTrashColumn1() { return trashColumn1; }
    public List<Ichimoku2TrashDto> getTrashColumn2() { return trashColumn2; }
    public List<Ichimoku2TrashDto> getTrashColumn3() { return trashColumn3; }
    public List<AnalysisLogEntry> getLog() { return log; }
    public void setLog(List<AnalysisLogEntry> v) { log = v; }
    public boolean isHasPreviousScan() { return hasPreviousScan; }
    public void setHasPreviousScan(boolean v) { hasPreviousScan = v; }
    public LocalDateTime getPreviousScanTime() { return previousScanTime; }
    public void setPreviousScanTime(LocalDateTime v) { previousScanTime = v; }
    public LocalDateTime getScanTime() { return scanTime; }
    public void setScanTime(LocalDateTime v) { scanTime = v; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String v) { dataSource = v; }
}
