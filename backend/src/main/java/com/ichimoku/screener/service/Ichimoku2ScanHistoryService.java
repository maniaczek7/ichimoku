package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.*;
import com.ichimoku.screener.entity.*;
import com.ichimoku.screener.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class Ichimoku2ScanHistoryService {
    private final Ichimoku2ScanRunRepository runRepo;
    private final Ichimoku2ScanResultRepository resultRepo;
    private final Ichimoku2TrashRepository trashRepo;

    public Ichimoku2ScanHistoryService(Ichimoku2ScanRunRepository runRepo, Ichimoku2ScanResultRepository resultRepo, Ichimoku2TrashRepository trashRepo) {
        this.runRepo = runRepo; this.resultRepo = resultRepo; this.trashRepo = trashRepo;
    }

    @Transactional
    public void saveAndCompare(Ichimoku2Response resp, boolean gpw, boolean us, String forexPairs) {
        LocalDateTime now = LocalDateTime.now();
        Optional<Ichimoku2ScanRun> prevOpt = runRepo.findLatest();
        Map<String, Ichimoku2ScanResult> prevMap = new HashMap<>();
        prevOpt.ifPresent(prev -> resultRepo.findByScanRunId(prev.getId()).forEach(r -> prevMap.put(key(r.getSymbol(), r.getDirection()), r)));

        Ichimoku2ScanRun run = new Ichimoku2ScanRun(now, gpw, us, forexPairs, resp.getDataSource(), resp.getLog().size());
        Set<String> current = new HashSet<>();
        saveCol(resp.getColumn1(), 1, prevMap, current, run, now);
        saveCol(resp.getColumn2(), 2, prevMap, current, run, now);
        saveCol(resp.getColumn3(), 3, prevMap, current, run, now);

        for (var e : prevMap.entrySet()) {
            if (!current.contains(e.getKey())) {
                Ichimoku2ScanResult p = e.getValue();
                Ichimoku2TrashEntry t = new Ichimoku2TrashEntry();
                t.setSymbol(p.getSymbol()); t.setMarket(p.getMarket()); t.setDirection(p.getDirection());
                t.setToken(p.getToken()); t.setPreviousColumn(p.getColumnNumber()); t.setScore(p.getScore());
                t.setCloudDays(p.getCloudDays()); t.setLastClose(p.getLastClose());
                t.setFirstSeenAt(p.getFirstSeenAt()); t.setLastSeenAt(prevOpt.map(Ichimoku2ScanRun::getScannedAt).orElse(now));
                t.setDroppedAt(now); t.setDropReason("NO_LONGER_QUALIFIES"); t.setDismissed(false);
                trashRepo.save(t);
            }
        }
        runRepo.save(run);
        resp.setHasPreviousScan(prevOpt.isPresent());
        resp.setPreviousScanTime(prevOpt.map(Ichimoku2ScanRun::getScannedAt).orElse(null));
        resp.setScanTime(now);
        loadTrash(resp);
    }

    private void saveCol(List<Ichimoku2Entry> entries, int col, Map<String, Ichimoku2ScanResult> prevMap, Set<String> current, Ichimoku2ScanRun run, LocalDateTime now) {
        for (Ichimoku2Entry e : entries) {
            String k = key(e.getSymbol(), e.getDirection());
            current.add(k);
            Ichimoku2ScanResult prev = prevMap.get(k);
            if (prev == null) e.setChangeStatus("NEW");
            else if (prev.getColumnNumber() < col) { e.setChangeStatus("MOVED_UP"); e.setPreviousColumn(prev.getColumnNumber()); }
            else if (prev.getColumnNumber() > col) { e.setChangeStatus("MOVED_DOWN"); e.setPreviousColumn(prev.getColumnNumber()); }
            else e.setChangeStatus("UNCHANGED");

            Ichimoku2ScanResult r = new Ichimoku2ScanResult();
            r.setSymbol(e.getSymbol()); r.setMarket(e.getMarket()); r.setDirection(e.getDirection());
            r.setToken(e.getToken()); r.setColumnNumber(col); r.setCloudDays(e.getCloudDays());
            r.setScore(e.getScore()); r.setLastClose(e.getLastClose());
            r.setFirstSeenAt(prev != null ? prev.getFirstSeenAt() : now);
            run.addResult(r);
        }
    }

    @Transactional(readOnly = true)
    public Optional<Ichimoku2Response> getLastScan() {
        return runRepo.findLatest().map(run -> {
            Ichimoku2Response resp = new Ichimoku2Response();
            resultRepo.findByScanRunId(run.getId()).forEach(r -> {
                Ichimoku2Entry e = toEntry(r);
                switch (r.getColumnNumber()) { case 1 -> resp.getColumn1().add(e); case 2 -> resp.getColumn2().add(e); case 3 -> resp.getColumn3().add(e); }
            });
            resp.setDataSource(run.getDataSource());
            resp.setScanTime(run.getScannedAt());
            loadTrash(resp);
            return resp;
        });
    }

    private void loadTrash(Ichimoku2Response resp) {
        trashRepo.findByDismissedFalseOrderByDroppedAtDesc().forEach(t -> {
            Ichimoku2TrashDto d = toTrashDto(t);
            switch (t.getPreviousColumn()) { case 1 -> resp.getTrashColumn1().add(d); case 2 -> resp.getTrashColumn2().add(d); default -> resp.getTrashColumn3().add(d); }
        });
    }

    @Transactional
    public boolean dismissTrash(Long id) {
        return trashRepo.findById(id).map(t -> { t.setDismissed(true); trashRepo.save(t); return true; }).orElse(false);
    }

    private static String key(String s, String d) { return (s + "|" + d).toUpperCase(); }

    private Ichimoku2Entry toEntry(Ichimoku2ScanResult r) {
        Ichimoku2Entry e = new Ichimoku2Entry();
        e.setSymbol(r.getSymbol()); e.setMarket(r.getMarket()); e.setDirection(r.getDirection());
        e.setToken(r.getToken()); e.setCloudDays(r.getCloudDays()); e.setScore(r.getScore()); e.setLastClose(r.getLastClose());
        return e;
    }

    private Ichimoku2TrashDto toTrashDto(Ichimoku2TrashEntry t) {
        Ichimoku2TrashDto d = new Ichimoku2TrashDto();
        d.setId(t.getId()); d.setSymbol(t.getSymbol()); d.setMarket(t.getMarket()); d.setDirection(t.getDirection());
        d.setToken(t.getToken()); d.setPreviousColumn(t.getPreviousColumn()); d.setScore(t.getScore());
        d.setCloudDays(t.getCloudDays()); d.setLastClose(t.getLastClose()); d.setDropReason(t.getDropReason());
        d.setFirstSeenAt(t.getFirstSeenAt()); d.setLastSeenAt(t.getLastSeenAt()); d.setDroppedAt(t.getDroppedAt());
        return d;
    }
}
