package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.Ichimoku2ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface Ichimoku2ScanResultRepository extends JpaRepository<Ichimoku2ScanResult, Long> {
    List<Ichimoku2ScanResult> findByScanRunId(Long scanRunId);
}
