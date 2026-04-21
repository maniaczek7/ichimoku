package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.FibonacciResponse;
import com.ichimoku.screener.dto.TrojpolowkaResponse;
import com.ichimoku.screener.repository.FibScanRunRepository;
import com.ichimoku.screener.repository.ScanRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ScheduledScanService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledScanService.class);

    private final ScreenerService screenerService;
    private final FibonacciService fibonacciService;
    private final ScanHistoryService scanHistoryService;
    private final FibScanHistoryService fibScanHistoryService;

    private final ScanRunRepository scanRunRepository;
    private final FibScanRunRepository fibScanRunRepository;

    private volatile LocalDate lastSuccessfulIchiDate = null;
    private volatile LocalDate lastSuccessfulFibDate = null;

    private static final String TIMEZONE = "Europe/Warsaw";
    private static final int TURNOVER_DAYS = 10;
    private static final double MIN_TURNOVER = 500_000;
    private static final int US_TOP_N = 200;
    private static final int STARTUP_DELAY_SECONDS = 30;

    public ScheduledScanService(ScreenerService screenerService,
                                FibonacciService fibonacciService,
                                ScanHistoryService scanHistoryService,
                                FibScanHistoryService fibScanHistoryService,
                                ScanRunRepository scanRunRepository,
                                FibScanRunRepository fibScanRunRepository) {
        this.screenerService = screenerService;
        this.fibonacciService = fibonacciService;
        this.scanHistoryService = scanHistoryService;
        this.fibScanHistoryService = fibScanHistoryService;
        this.scanRunRepository = scanRunRepository;
        this.fibScanRunRepository = fibScanRunRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartupCatchUp() {
        ZoneId warsawZone = ZoneId.of(TIMEZONE);
        LocalDate today = LocalDate.now(warsawZone);
        boolean isWeekday = today.getDayOfWeek().getValue() <= 5;

        if (!isWeekday) {
            log.info("[STARTUP] Weekend — skipping catch-up scan");
            return;
        }

        LocalDate scanDate = lastExpectedScanDate(today, warsawZone);

        boolean ichiDone = scanRunRepository.findLatest()
                .map(run -> !run.getScannedAt().toLocalDate().isBefore(scanDate))
                .orElse(false);
        boolean fibDone = fibScanRunRepository.findLatest()
                .map(run -> !run.getScannedAt().toLocalDate().isBefore(scanDate))
                .orElse(false);

        if (ichiDone) lastSuccessfulIchiDate = scanDate;
        if (fibDone) lastSuccessfulFibDate = scanDate;

        if (ichiDone && fibDone) {
            log.info("[STARTUP] Scans already completed for {} — no catch-up needed", scanDate);
            return;
        }

        log.info("[STARTUP] Catch-up needed for {} (ichi done: {}, fib done: {}). Will run in {}s...",
                scanDate, ichiDone, fibDone, STARTUP_DELAY_SECONDS);

        final boolean runIchi = !ichiDone;
        final boolean runFib = !fibDone;
        new Thread(() -> {
            try {
                Thread.sleep(STARTUP_DELAY_SECONDS * 1000L);
                if (runIchi) runIchimokuScan("startup-catchup");
                if (runFib) runFibonacciScan("startup-catchup");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "startup-catchup-scan").start();
    }

    /**
     * Returns the date of the last expected scan:
     * - 22:00+: today (the 22:00 scan should have run already)
     * - Before 22:00: previous business day
     */
    private LocalDate lastExpectedScanDate(LocalDate today, ZoneId zone) {
        int hour = java.time.ZonedDateTime.now(zone).getHour();
        if (hour >= 22) return today;
        LocalDate prev = today.minusDays(1);
        while (prev.getDayOfWeek().getValue() > 5) prev = prev.minusDays(1);
        return prev;
    }

    private List<String> allForexPairs() {
        List<String> all = new ArrayList<>();
        all.addAll(ScreenerService.FOREX_MAJOR);
        all.addAll(ScreenerService.FOREX_MINOR);
        all.addAll(ScreenerService.FOREX_PLN);
        return all;
    }

    private String forexPairsAsString() {
        return String.join(",", allForexPairs());
    }

    /**
     * 22:00 Warsaw, Mon-Fri. Primary scan — all markets closed, full daily data available.
     */
    @Scheduled(cron = "0 0 22 * * MON-FRI", zone = TIMEZONE)
    public void eveningScan() {
        log.info("[SCHEDULED] Evening scan triggered at {}", LocalDateTime.now());
        runFullScan("evening");
    }

    /**
     * 8:00 Warsaw, Mon-Fri. Retry if yesterday's 22:00 scan failed or was missed.
     */
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = TIMEZONE)
    public void morningScan() {
        LocalDate today = LocalDate.now(ZoneId.of(TIMEZONE));
        LocalDate expectedScanDate = previousBusinessDay(today);

        boolean ichiNeeded = lastSuccessfulIchiDate == null || lastSuccessfulIchiDate.isBefore(expectedScanDate);
        boolean fibNeeded = lastSuccessfulFibDate == null || lastSuccessfulFibDate.isBefore(expectedScanDate);

        if (!ichiNeeded && !fibNeeded) {
            log.info("[SCHEDULED] Morning retry skipped — scans up to date (last ichi: {}, last fib: {})",
                    lastSuccessfulIchiDate, lastSuccessfulFibDate);
            return;
        }

        log.info("[SCHEDULED] Morning retry triggered at {} (ichi needed: {}, fib needed: {}, expected: {})",
                LocalDateTime.now(), ichiNeeded, fibNeeded, expectedScanDate);

        if (ichiNeeded) runIchimokuScan("morning-retry");
        if (fibNeeded) runFibonacciScan("morning-retry");
    }

    private LocalDate previousBusinessDay(LocalDate date) {
        LocalDate prev = date.minusDays(1);
        while (prev.getDayOfWeek().getValue() > 5) prev = prev.minusDays(1);
        return prev;
    }

    private void runFullScan(String label) {
        scanRunning = true;
        lastError = null;
        try {
            runIchimokuScan(label);
            runFibonacciScan(label);
        } finally {
            scanRunning = false;
        }
    }

    private void runIchimokuScan(String label) {
        try {
            log.info("[SCHEDULED][{}] Starting Ichimoku scan...", label);
            List<String> fxPairs = allForexPairs();

            TrojpolowkaResponse response = screenerService.runTrojpolowkaScan(
                    true, true, fxPairs, TURNOVER_DAYS, MIN_TURNOVER, US_TOP_N);

            scanHistoryService.saveAndCompare(response, true, true, forexPairsAsString());

            int total = response.getColumn1().size() + response.getColumn2().size() + response.getColumn3().size();
            lastSuccessfulIchiDate = LocalDate.now(ZoneId.of(TIMEZONE));
            log.info("[SCHEDULED][{}] Ichimoku scan OK - {} signals found", label, total);
        } catch (Exception e) {
            lastError = "[" + label + "] Ichimoku: " + e.getMessage();
            log.error("[SCHEDULED][{}] Ichimoku scan FAILED: {}", label, e.getMessage(), e);
        }
    }

    private void runFibonacciScan(String label) {
        try {
            log.info("[SCHEDULED][{}] Starting Fibonacci scan...", label);
            List<String> fxPairs = allForexPairs();

            FibonacciResponse response = fibonacciService.runScan(
                    true, true, fxPairs, TURNOVER_DAYS, MIN_TURNOVER, US_TOP_N);

            fibScanHistoryService.saveAndCompare(response, true, true, forexPairsAsString());

            int total = response.getColumn1().size() + response.getColumn2().size() + response.getColumn3().size();
            lastSuccessfulFibDate = LocalDate.now(ZoneId.of(TIMEZONE));
            log.info("[SCHEDULED][{}] Fibonacci scan OK - {} signals found", label, total);
        } catch (Exception e) {
            lastError = "[" + label + "] Fibonacci: " + e.getMessage();
            log.error("[SCHEDULED][{}] Fibonacci scan FAILED: {}", label, e.getMessage(), e);
        }
    }

    // --- Status & manual trigger ---

    private volatile boolean scanRunning = false;
    private volatile String lastError = null;

    public Map<String, Object> getScanStatus() {
        ZoneId zone = ZoneId.of(TIMEZONE);
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> status = new LinkedHashMap<>();

        status.put("currentTime", now.toLocalDateTime().toString());
        status.put("timezone", TIMEZONE);
        status.put("scanRunning", scanRunning);

        Map<String, Object> ichi = new LinkedHashMap<>();
        ichi.put("lastSuccessfulDate", lastSuccessfulIchiDate);
        scanRunRepository.findLatest().ifPresent(run -> {
            ichi.put("lastScanAt", run.getScannedAt().toString());
            ichi.put("totalAnalyzed", run.getTotalAnalyzed());
        });
        status.put("ichimoku", ichi);

        Map<String, Object> fib = new LinkedHashMap<>();
        fib.put("lastSuccessfulDate", lastSuccessfulFibDate);
        fibScanRunRepository.findLatest().ifPresent(run -> {
            fib.put("lastScanAt", run.getScannedAt().toString());
            fib.put("totalAnalyzed", run.getTotalAnalyzed());
        });
        status.put("fibonacci", fib);

        status.put("nextEveningScan", nextCronFire(now, 22));
        status.put("nextMorningRetry", nextCronFire(now, 8));

        if (lastError != null) {
            status.put("lastError", lastError);
        }

        return status;
    }

    private String nextCronFire(ZonedDateTime now, int hour) {
        ZonedDateTime candidate = now.toLocalDate().atTime(hour, 0).atZone(now.getZone());
        if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        return candidate.toLocalDateTime().toString();
    }

    public Map<String, Object> triggerManualScan() {
        if (scanRunning) {
            return Map.of("status", "rejected", "reason", "Scan already in progress");
        }

        log.info("[MANUAL] Manual scan triggered at {}", LocalDateTime.now());
        new Thread(() -> {
            scanRunning = true;
            lastError = null;
            try {
                runFullScan("manual");
            } catch (Exception e) {
                lastError = e.getMessage();
                log.error("[MANUAL] Manual scan FAILED: {}", e.getMessage(), e);
            } finally {
                scanRunning = false;
            }
        }, "manual-scan").start();

        return Map.of("status", "started", "message", "Full scan triggered in background");
    }
}
