package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.FibScanRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FibScanRunRepository extends JpaRepository<FibScanRun, Long> {

    @Query("SELECT sr FROM FibScanRun sr ORDER BY sr.scannedAt DESC LIMIT 1")
    Optional<FibScanRun> findLatest();

    @Query("SELECT sr FROM FibScanRun sr WHERE sr.id < :currentId ORDER BY sr.scannedAt DESC LIMIT 1")
    Optional<FibScanRun> findPreviousBefore(Long currentId);
}
