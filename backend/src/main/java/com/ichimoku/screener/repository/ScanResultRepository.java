package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {

    List<ScanResult> findByScanRunId(Long scanRunId);
}
