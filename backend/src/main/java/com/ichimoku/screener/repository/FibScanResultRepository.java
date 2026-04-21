package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.FibScanResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FibScanResultRepository extends JpaRepository<FibScanResult, Long> {

    List<FibScanResult> findByScanRunId(Long scanRunId);
}
