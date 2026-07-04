package com.ichimoku.screener.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ichimoku2_scan_run")
public class Ichimoku2ScanRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime scannedAt;
    private boolean gpw, us;
    private String forexPairs, dataSource;
    private int totalAnalyzed;

    @OneToMany(mappedBy = "scanRun", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Ichimoku2ScanResult> results = new ArrayList<>();

    public Ichimoku2ScanRun() {}
    public Ichimoku2ScanRun(LocalDateTime scannedAt, boolean gpw, boolean us, String forexPairs, String dataSource, int totalAnalyzed) {
        this.scannedAt = scannedAt; this.gpw = gpw; this.us = us; this.forexPairs = forexPairs;
        this.dataSource = dataSource; this.totalAnalyzed = totalAnalyzed;
    }
    public Long getId() { return id; }
    public LocalDateTime getScannedAt() { return scannedAt; }
    public String getDataSource() { return dataSource; }
    public void addResult(Ichimoku2ScanResult r) { results.add(r); r.setScanRun(this); }
}
