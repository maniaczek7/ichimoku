package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.ScanRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ScanRunRepository extends JpaRepository<ScanRun, Long> {

    @Query("SELECT sr FROM ScanRun sr ORDER BY sr.scannedAt DESC LIMIT 1")
    Optional<ScanRun> findLatest();

    @Query("SELECT sr FROM ScanRun sr WHERE sr.id < :currentId ORDER BY sr.scannedAt DESC LIMIT 1")
    Optional<ScanRun> findPreviousBefore(Long currentId);
}
