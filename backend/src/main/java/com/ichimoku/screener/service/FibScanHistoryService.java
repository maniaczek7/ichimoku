package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.FibDroppedEntry;
import com.ichimoku.screener.dto.FibonacciEntry;
import com.ichimoku.screener.dto.FibonacciResponse;
import com.ichimoku.screener.entity.FibScanResult;
import com.ichimoku.screener.entity.FibScanRun;
import com.ichimoku.screener.repository.FibScanResultRepository;
import com.ichimoku.screener.repository.FibScanRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FibScanHistoryService {

    private static final Logger log = LoggerFactory.getLogger(FibScanHistoryService.class);

    private final FibScanRunRepository runRepo;
    private final FibScanResultRepository resultRepo;

    public FibScanHistoryService(FibScanRunRepository runRepo, FibScanResultRepository resultRepo) {
        this.runRepo = runRepo;
        this.resultRepo = resultRepo;
    }

    @Transactional
    public void saveAndCompare(FibonacciResponse response, boolean gpw, boolean us, String forexPairs) {
        Optional<FibScanRun> previousRunOpt = runRepo.findLatest();

        Map<String, FibScanResult> previousMap = new HashMap<>();
        previousRunOpt.ifPresent(prev -> {
            for (FibScanResult r : resultRepo.findByScanRunId(prev.getId())) {
                previousMap.put(buildKey(r.getSymbol(), r.getDirection()), r);
            }
        });

        FibScanRun newRun = new FibScanRun(
                LocalDateTime.now(), gpw, us, forexPairs,
                response.getDataSource(),
                response.getLog() != null ? response.getLog().size() : 0
        );

        Set<String> currentKeys = new HashSet<>();
        applyChanges(response.getColumn1(), 1, previousMap, currentKeys, newRun);
        applyChanges(response.getColumn2(), 2, previousMap, currentKeys, newRun);
        applyChanges(response.getColumn3(), 3, previousMap, currentKeys, newRun);

        List<FibDroppedEntry> dropped = new ArrayList<>();
        for (Map.Entry<String, FibScanResult> entry : previousMap.entrySet()) {
            if (!currentKeys.contains(entry.getKey())) {
                FibScanResult prev = entry.getValue();
                dropped.add(new FibDroppedEntry(
                        prev.getSymbol(), prev.getMarket(), prev.getDirection(),
                        prev.getColumnNumber(), prev.getLastClose()
                ));
            }
        }
        dropped.sort(Comparator.comparingInt(FibDroppedEntry::getPreviousColumn).reversed());
        response.setDropped(dropped);

        response.setHasPreviousScan(previousRunOpt.isPresent());
        response.setPreviousScanTime(previousRunOpt.map(FibScanRun::getScannedAt).orElse(null));
        response.setScanTime(newRun.getScannedAt());

        runRepo.save(newRun);

        log.info("Saved fib scan #{} with {} results, {} dropped", newRun.getId(), newRun.getResults().size(), dropped.size());
    }

    private void applyChanges(List<FibonacciEntry> entries, int column,
                              Map<String, FibScanResult> previousMap,
                              Set<String> currentKeys, FibScanRun newRun) {
        for (FibonacciEntry entry : entries) {
            String key = buildKey(entry.getSymbol(), entry.getDirection());
            currentKeys.add(key);

            FibScanResult prev = previousMap.get(key);
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

            newRun.addResult(toResult(entry, column));
        }
    }

    @Transactional(readOnly = true)
    public Optional<FibonacciResponse> getLastScan() {
        Optional<FibScanRun> latestOpt = runRepo.findLatest();
        if (latestOpt.isEmpty()) return Optional.empty();

        FibScanRun latest = latestOpt.get();
        List<FibScanResult> results = resultRepo.findByScanRunId(latest.getId());
        if (results.isEmpty()) return Optional.empty();

        FibonacciResponse response = new FibonacciResponse();
        for (FibScanResult r : results) {
            FibonacciEntry entry = toEntry(r);
            switch (r.getColumnNumber()) {
                case 1 -> response.getColumn1().add(entry);
                case 2 -> response.getColumn2().add(entry);
                case 3 -> response.getColumn3().add(entry);
            }
        }

        Optional<FibScanRun> previousOpt = runRepo.findPreviousBefore(latest.getId());
        if (previousOpt.isPresent()) {
            List<FibScanResult> prevResults = resultRepo.findByScanRunId(previousOpt.get().getId());
            Map<String, FibScanResult> prevMap = new HashMap<>();
            for (FibScanResult r : prevResults) {
                prevMap.put(buildKey(r.getSymbol(), r.getDirection()), r);
            }

            Set<String> currentKeys = new HashSet<>();
            applyChangeStatus(response.getColumn1(), 1, prevMap, currentKeys);
            applyChangeStatus(response.getColumn2(), 2, prevMap, currentKeys);
            applyChangeStatus(response.getColumn3(), 3, prevMap, currentKeys);

            List<FibDroppedEntry> dropped = new ArrayList<>();
            for (Map.Entry<String, FibScanResult> entry : prevMap.entrySet()) {
                if (!currentKeys.contains(entry.getKey())) {
                    FibScanResult prev = entry.getValue();
                    dropped.add(new FibDroppedEntry(
                            prev.getSymbol(), prev.getMarket(), prev.getDirection(),
                            prev.getColumnNumber(), prev.getLastClose()
                    ));
                }
            }
            dropped.sort(Comparator.comparingInt(FibDroppedEntry::getPreviousColumn).reversed());
            response.setDropped(dropped);
            response.setHasPreviousScan(true);
            response.setPreviousScanTime(previousOpt.get().getScannedAt());
        }

        response.setDataSource(latest.getDataSource());
        response.setScanTime(latest.getScannedAt());
        return Optional.of(response);
    }

    private void applyChangeStatus(List<FibonacciEntry> entries, int column,
                                   Map<String, FibScanResult> prevMap, Set<String> currentKeys) {
        for (FibonacciEntry entry : entries) {
            String key = buildKey(entry.getSymbol(), entry.getDirection());
            currentKeys.add(key);
            FibScanResult prev = prevMap.get(key);
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

    private FibScanResult toResult(FibonacciEntry e, int column) {
        FibScanResult r = new FibScanResult();
        r.setSymbol(e.getSymbol());
        r.setMarket(e.getMarket());
        r.setDirection(e.getDirection());
        r.setColumnNumber(column);
        r.setLastClose(e.getLastClose());
        r.setImpulseStartDate(e.getImpulseStartDate());
        r.setImpulseEndDate(e.getImpulseEndDate());
        r.setImpulseStartPrice(e.getImpulseStartPrice());
        r.setImpulseEndPrice(e.getImpulseEndPrice());
        r.setImpulseHeight(e.getImpulseHeight());
        r.setImpulsePct(e.getImpulsePct());
        r.setImpulseDays(e.getImpulseDays());
        r.setFib236(e.getFib236());
        r.setFib382(e.getFib382());
        r.setFib500(e.getFib500());
        r.setFib618(e.getFib618());
        r.setFib786(e.getFib786());
        r.setRetracementPct(e.getRetracementPct());
        r.setRetracementFromPeak(e.getRetracementFromPeak());
        r.setNearestFibLevel(e.getNearestFibLevel());
        r.setDistToFib50Pct(e.getDistToFib50Pct());
        r.setFibBounce(e.isFibBounce());
        r.setInPlay(e.isInPlay());
        r.setStopLoss(e.getStopLoss());
        r.setBouncePatternDate(e.getBouncePatternDate());
        if (e.getPatterns() != null && !e.getPatterns().isEmpty()) {
            r.setPatternsJson(String.join("||", e.getPatterns()));
        }
        if (e.getSignals() != null && !e.getSignals().isEmpty()) {
            r.setSignalsJson(String.join("||", e.getSignals()));
        }
        return r;
    }

    private FibonacciEntry toEntry(FibScanResult r) {
        FibonacciEntry e = new FibonacciEntry();
        e.setSymbol(r.getSymbol());
        e.setMarket(r.getMarket());
        e.setDirection(r.getDirection());
        e.setColumn(r.getColumnNumber());
        e.setLastClose(r.getLastClose());
        e.setImpulseStartDate(r.getImpulseStartDate());
        e.setImpulseEndDate(r.getImpulseEndDate());
        e.setImpulseStartPrice(r.getImpulseStartPrice());
        e.setImpulseEndPrice(r.getImpulseEndPrice());
        e.setImpulseHeight(r.getImpulseHeight());
        e.setImpulsePct(r.getImpulsePct());
        e.setImpulseDays(r.getImpulseDays());
        e.setFib236(r.getFib236());
        e.setFib382(r.getFib382());
        e.setFib500(r.getFib500());
        e.setFib618(r.getFib618());
        e.setFib786(r.getFib786());
        e.setRetracementPct(r.getRetracementPct());
        e.setRetracementFromPeak(r.getRetracementFromPeak());
        e.setNearestFibLevel(r.getNearestFibLevel());
        e.setDistToFib50Pct(r.getDistToFib50Pct());
        e.setFibBounce(r.isFibBounce());
        e.setInPlay(r.isInPlay());
        e.setStopLoss(r.getStopLoss());
        e.setBouncePatternDate(r.getBouncePatternDate());
        if (r.getPatternsJson() != null && !r.getPatternsJson().isEmpty()) {
            e.setPatterns(new ArrayList<>(List.of(r.getPatternsJson().split("\\|\\|"))));
        }
        if (r.getSignalsJson() != null && !r.getSignalsJson().isEmpty()) {
            e.setSignals(new ArrayList<>(List.of(r.getSignalsJson().split("\\|\\|"))));
        }
        return e;
    }

    private String buildKey(String symbol, String direction) {
        return (symbol + "|" + direction).toUpperCase();
    }
}
