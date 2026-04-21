package com.ichimoku.screener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "fib_scan_run")
public class FibScanRun {

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
    private List<FibScanResult> results = new ArrayList<>();

    public FibScanRun() {}

    public FibScanRun(LocalDateTime scannedAt, boolean gpw, boolean us,
                      String forexPairs, String dataSource, int totalAnalyzed) {
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
    public boolean isUs() { return us; }
    public String getForexPairs() { return forexPairs; }
    public String getDataSource() { return dataSource; }
    public int getTotalAnalyzed() { return totalAnalyzed; }
    public List<FibScanResult> getResults() { return results; }

    public void addResult(FibScanResult result) {
        results.add(result);
        result.setScanRun(this);
    }
}
