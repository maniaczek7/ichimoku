package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.DroppedEntry;
import com.ichimoku.screener.dto.TrojpolowkaEntry;
import com.ichimoku.screener.dto.TrojpolowkaResponse;
import com.ichimoku.screener.entity.ScanResult;
import com.ichimoku.screener.entity.ScanRun;
import com.ichimoku.screener.repository.ScanResultRepository;
import com.ichimoku.screener.repository.ScanRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ScanHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ScanHistoryService.class);

    private final ScanRunRepository scanRunRepository;
    private final ScanResultRepository scanResultRepository;

    public ScanHistoryService(ScanRunRepository scanRunRepository, ScanResultRepository scanResultRepository) {
        this.scanRunRepository = scanRunRepository;
        this.scanResultRepository = scanResultRepository;
    }

    @Transactional
    public void saveAndCompare(TrojpolowkaResponse response, boolean gpw, boolean us, String forexPairs) {
        Optional<ScanRun> previousRunOpt = scanRunRepository.findLatest();

        Map<String, ScanResult> previousMap = new HashMap<>();
        previousRunOpt.ifPresent(prev -> {
            List<ScanResult> prevResults = scanResultRepository.findByScanRunId(prev.getId());
            for (ScanResult r : prevResults) {
                previousMap.put(buildKey(r.getSymbol(), r.getDirection()), r);
            }
        });

        ScanRun newRun = new ScanRun(
                LocalDateTime.now(), gpw, us, forexPairs,
                response.getDataSource(),
                (response.getLog() != null ? response.getLog().size() : 0)
        );

        Set<String> currentKeys = new HashSet<>();
        applyChanges(response.getColumn1(), 1, previousMap, currentKeys, newRun);
        applyChanges(response.getColumn2(), 2, previousMap, currentKeys, newRun);
        applyChanges(response.getColumn3(), 3, previousMap, currentKeys, newRun);

        List<DroppedEntry> dropped = new ArrayList<>();
        for (Map.Entry<String, ScanResult> entry : previousMap.entrySet()) {
            if (!currentKeys.contains(entry.getKey())) {
                ScanResult prev = entry.getValue();
                dropped.add(new DroppedEntry(
                        prev.getSymbol(), prev.getMarket(), prev.getDirection(),
                        prev.getColumnNumber(), prev.getLastClose()
                ));
            }
        }
        dropped.sort(Comparator.comparingInt(DroppedEntry::getPreviousColumn).reversed());
        response.setDropped(dropped);

        boolean hasPrevious = previousRunOpt.isPresent();
        response.setHasPreviousScan(hasPrevious);
        response.setPreviousScanTime(previousRunOpt.map(ScanRun::getScannedAt).orElse(null));

        scanRunRepository.save(newRun);

        log.info("Saved scan run #{} with {} results, {} dropped from previous",
                newRun.getId(), newRun.getResults().size(), dropped.size());
    }

    private void applyChanges(List<TrojpolowkaEntry> entries, int column,
                              Map<String, ScanResult> previousMap,
                              Set<String> currentKeys, ScanRun newRun) {
        for (TrojpolowkaEntry entry : entries) {
            String key = buildKey(entry.getSymbol(), entry.getDirection());
            currentKeys.add(key);

            ScanResult prev = previousMap.get(key);
            if (prev == null) {
                entry.setChangeStatus("NEW");
            } else if (prev.getColumnNumber() < column) {
                entry.setChangeStatus("MOVED_UP");
                entry.setPreviousColumn(prev.getColumnNumber());
            } else if (prev.getColumnNumber() > column) {
                entry.setChangeStatus("MOVED_DOWN");
                entry.setPreviousColumn(prev.getColumnNumber());
            } else {
                entry.setChangeStatus("UNCHANGED");
            }

            ScanResult result = toScanResult(entry, column);
            newRun.addResult(result);
        }
    }

    private ScanResult toScanResult(TrojpolowkaEntry entry, int column) {
        ScanResult r = new ScanResult();
        r.setSymbol(entry.getSymbol());
        r.setMarket(entry.getMarket());
        r.setDirection(entry.getDirection());
        r.setColumnNumber(column);
        r.setCloudDays(entry.getCloudDays());
        r.setCloudPosition(entry.getCloudPosition());
        r.setScore(entry.getScore());
        r.setCol3Status(entry.getCol3Status());
        r.setLastClose(entry.getLastClose());
        r.setAvgTurnover10d(entry.getAvgTurnover10d());
        r.setBreakoutDetected(entry.isBreakoutDetected());
        r.setBreakoutDaysAgo(entry.getBreakoutDaysAgo());
        r.setTkCross(entry.isTkCross());
        r.setTkCrossDirection(entry.getTkCrossDirection());
        r.setDaysSinceTkCross(entry.getDaysSinceTkCross());
        r.setTkSwapped(entry.isTkSwapped());
        r.setChikouConfirms(entry.isChikouConfirms());
        r.setChikouValue(entry.getChikouValue());
        r.setPriceAtChikou(entry.getPriceAtChikou());
        r.setKumoTwist(entry.isKumoTwist());
        r.setPositiveRetest(entry.isPositiveRetest());
        r.setDetectedPattern(entry.getDetectedPattern());
        r.setRetestZone(entry.getRetestZone());
        r.setNearCloudEdge(entry.isNearCloudEdge());
        if (entry.getSignals() != null && !entry.getSignals().isEmpty()) {
            r.setSignalsJson(String.join("||", entry.getSignals()));
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Optional<TrojpolowkaResponse> getLastScan() {
        Optional<ScanRun> latestOpt = scanRunRepository.findLatest();
        if (latestOpt.isEmpty()) return Optional.empty();

        ScanRun latest = latestOpt.get();
        List<ScanResult> results = scanResultRepository.findByScanRunId(latest.getId());
        if (results.isEmpty()) return Optional.empty();

        TrojpolowkaResponse response = new TrojpolowkaResponse();
        for (ScanResult r : results) {
            TrojpolowkaEntry entry = toEntry(r);
            switch (r.getColumnNumber()) {
                case 1 -> response.getColumn1().add(entry);
                case 2 -> response.getColumn2().add(entry);
                case 3 -> response.getColumn3().add(entry);
            }
        }

        Optional<ScanRun> previousOpt = scanRunRepository.findPreviousBefore(latest.getId());
        if (previousOpt.isPresent()) {
            List<ScanResult> prevResults = scanResultRepository.findByScanRunId(previousOpt.get().getId());
            Map<String, ScanResult> prevMap = new HashMap<>();
            for (ScanResult r : prevResults) {
                prevMap.put(buildKey(r.getSymbol(), r.getDirection()), r);
            }

            Set<String> currentKeys = new HashSet<>();
            applyChangeStatus(response.getColumn1(), 1, prevMap, currentKeys);
            applyChangeStatus(response.getColumn2(), 2, prevMap, currentKeys);
            applyChangeStatus(response.getColumn3(), 3, prevMap, currentKeys);

            List<DroppedEntry> dropped = new ArrayList<>();
            for (Map.Entry<String, ScanResult> entry : prevMap.entrySet()) {
                if (!currentKeys.contains(entry.getKey())) {
                    ScanResult prev = entry.getValue();
                    dropped.add(new DroppedEntry(
                            prev.getSymbol(), prev.getMarket(), prev.getDirection(),
                            prev.getColumnNumber(), prev.getLastClose()
                    ));
                }
            }
            dropped.sort(Comparator.comparingInt(DroppedEntry::getPreviousColumn).reversed());
            response.setDropped(dropped);
            response.setHasPreviousScan(true);
            response.setPreviousScanTime(previousOpt.get().getScannedAt());
        }

        response.setDataSource(latest.getDataSource());
        response.setScanTime(latest.getScannedAt());
        return Optional.of(response);
    }

    private void applyChangeStatus(List<TrojpolowkaEntry> entries, int column,
                                   Map<String, ScanResult> prevMap, Set<String> currentKeys) {
        for (TrojpolowkaEntry entry : entries) {
            String key = buildKey(entry.getSymbol(), entry.getDirection());
            currentKeys.add(key);
            ScanResult prev = prevMap.get(key);
            if (prev == null) {
                entry.setChangeStatus("NEW");
            } else if (prev.getColumnNumber() < column) {
                entry.setChangeStatus("MOVED_UP");
                entry.setPreviousColumn(prev.getColumnNumber());
            } else if (prev.getColumnNumber() > column) {
                entry.setChangeStatus("MOVED_DOWN");
                entry.setPreviousColumn(prev.getColumnNumber());
            } else {
                entry.setChangeStatus("UNCHANGED");
            }
        }
    }

    private TrojpolowkaEntry toEntry(ScanResult r) {
        TrojpolowkaEntry e = new TrojpolowkaEntry();
        e.setSymbol(r.getSymbol());
        e.setMarket(r.getMarket());
        e.setDirection(r.getDirection());
        e.setCloudDays(r.getCloudDays());
        e.setCloudPosition(r.getCloudPosition());
        e.setScore(r.getScore());
        e.setCol3Status(r.getCol3Status());
        e.setLastClose(r.getLastClose());
        e.setAvgTurnover10d(r.getAvgTurnover10d());
        e.setBreakoutDetected(r.isBreakoutDetected());
        e.setBreakoutDaysAgo(r.getBreakoutDaysAgo());
        e.setTkCross(r.isTkCross());
        e.setTkCrossDirection(r.getTkCrossDirection());
        e.setDaysSinceTkCross(r.getDaysSinceTkCross());
        e.setTkSwapped(r.isTkSwapped());
        e.setChikouConfirms(r.isChikouConfirms());
        e.setChikouValue(r.getChikouValue());
        e.setPriceAtChikou(r.getPriceAtChikou());
        e.setKumoTwist(r.isKumoTwist());
        e.setPositiveRetest(r.isPositiveRetest());
        e.setDetectedPattern(r.getDetectedPattern());
        e.setRetestZone(r.getRetestZone());
        e.setNearCloudEdge(r.isNearCloudEdge());
        if (r.getSignalsJson() != null && !r.getSignalsJson().isEmpty()) {
            e.setSignals(new ArrayList<>(List.of(r.getSignalsJson().split("\\|\\|"))));
        }
        return e;
    }

    private String buildKey(String symbol, String direction) {
        return (symbol + "|" + direction).toUpperCase();
    }
}
