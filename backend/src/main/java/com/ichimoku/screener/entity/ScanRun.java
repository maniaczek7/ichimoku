package com.ichimoku.screener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scan_run")
public class ScanRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime scannedAt;
    private boolean gpw;
    private boolean us;
    private String forexPairs;
    private String dataSource;
    private int totalAnalyzed;

    @OneToMany(mappedBy = "scanRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScanResult> results = new ArrayList<>();

    public ScanRun() {}

    public ScanRun(LocalDateTime scannedAt, boolean gpw, boolean us, String forexPairs, String dataSource, int totalAnalyzed) {
        this.scannedAt = scannedAt;
        this.gpw = gpw;
        this.us = us;
        this.forexPairs = forexPairs;
        this.dataSource = dataSource;
        this.totalAnalyzed = totalAnalyzed;
    }

    public Long getId() { return id; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public void setScannedAt(LocalDateTime scannedAt) { this.scannedAt = scannedAt; }
    public boolean isGpw() { return gpw; }
    public void setGpw(boolean gpw) { this.gpw = gpw; }
    public boolean isUs() { return us; }
    public void setUs(boolean us) { this.us = us; }
    public String getForexPairs() { return forexPairs; }
    public void setForexPairs(String forexPairs) { this.forexPairs = forexPairs; }
    public String getDataSource() { return dataSource; }
    public void setDataSource(String dataSource) { this.dataSource = dataSource; }
    public int getTotalAnalyzed() { return totalAnalyzed; }
    public void setTotalAnalyzed(int totalAnalyzed) { this.totalAnalyzed = totalAnalyzed; }
    public List<ScanResult> getResults() { return results; }
    public void setResults(List<ScanResult> results) { this.results = results; }

    public void addResult(ScanResult result) {
        results.add(result);
        result.setScanRun(this);
    }
}
