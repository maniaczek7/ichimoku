package com.ichimoku.screener.controller;

import com.ichimoku.screener.dto.Ichimoku2BacktestResult;
import com.ichimoku.screener.dto.Ichimoku2Response;
import com.ichimoku.screener.service.Ichimoku2BacktestService;
import com.ichimoku.screener.service.Ichimoku2ScanHistoryService;
import com.ichimoku.screener.service.Ichimoku2Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/ichimoku2")
public class Ichimoku2Controller {
    private final Ichimoku2Service service;
    private final Ichimoku2ScanHistoryService history;
    private final Ichimoku2BacktestService backtest;

    public Ichimoku2Controller(Ichimoku2Service service, Ichimoku2ScanHistoryService history, Ichimoku2BacktestService backtest) {
        this.service = service; this.history = history; this.backtest = backtest;
    }

    @GetMapping("/scan")
    public ResponseEntity<Ichimoku2Response> scan(
            @RequestParam(defaultValue = "true") boolean gpw, @RequestParam(defaultValue = "true") boolean us,
            @RequestParam(defaultValue = "") String forexPairs, @RequestParam(defaultValue = "10") int turnoverDays,
            @RequestParam(defaultValue = "500000") double minTurnover, @RequestParam(defaultValue = "200") int usTopN) {
        List<String> fx = parseForex(forexPairs);
        Ichimoku2Response resp = service.runScan(gpw, us, fx, turnoverDays, minTurnover, Math.min(usTopN, 500));
        history.saveAndCompare(resp, gpw, us, forexPairs);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/last-scan")
    public ResponseEntity<Ichimoku2Response> lastScan() {
        return history.getLastScan().map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Map<String, Boolean>> dismiss(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("dismissed", history.dismissTrash(id)));
    }

    @GetMapping("/backtest")
    public ResponseEntity<Ichimoku2BacktestResult> backtest(
            @RequestParam(defaultValue = "true") boolean gpw, @RequestParam(defaultValue = "true") boolean us,
            @RequestParam(defaultValue = "") String forexPairs, @RequestParam(defaultValue = "200") int usTopN,
            @RequestParam(defaultValue = "CONFIRMED_LOWS") String slMethod,
            @RequestParam(required = false) String dateFrom, @RequestParam(required = false) String dateTo) {
        LocalDate from = dateFrom != null && !dateFrom.isBlank() ? LocalDate.parse(dateFrom) : null;
        LocalDate to = dateTo != null && !dateTo.isBlank() ? LocalDate.parse(dateTo) : null;
        return ResponseEntity.ok(backtest.run(gpw, us, parseForex(forexPairs), Math.min(usTopN, 500), slMethod, from, to));
    }

    private List<String> parseForex(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(String::toLowerCase).toList();
    }
}
