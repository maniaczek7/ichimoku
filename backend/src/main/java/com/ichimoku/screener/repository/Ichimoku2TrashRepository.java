package com.ichimoku.screener.repository;

import com.ichimoku.screener.entity.Ichimoku2TrashEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface Ichimoku2TrashRepository extends JpaRepository<Ichimoku2TrashEntry, Long> {
    List<Ichimoku2TrashEntry> findByDismissedFalseOrderByDroppedAtDesc();
}
