package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.Ichimoku2ScanRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface Ichimoku2ScanRunRepository extends JpaRepository<Ichimoku2ScanRun, Long> {
    @Query("SELECT sr FROM Ichimoku2ScanRun sr ORDER BY sr.scannedAt DESC LIMIT 1")
    Optional<Ichimoku2ScanRun> findLatest();
}
