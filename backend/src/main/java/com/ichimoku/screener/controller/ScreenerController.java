package com.ichimoku.screener.controller;

import com.ichimoku.screener.dto.ChartDataResponse;
import com.ichimoku.screener.dto.TrojpolowkaResponse;
import com.ichimoku.screener.service.ScanHistoryService;
import com.ichimoku.screener.service.ScheduledScanService;
import com.ichimoku.screener.service.ScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/screener")
@Tag(name = "Screener", description = "Ichimoku Trojpolowka D1 LONG/SHORT")
public class ScreenerController {

    private final ScreenerService screenerService;
    private final ScanHistoryService scanHistoryService;
    private final ScheduledScanService scheduledScanService;

    public ScreenerController(ScreenerService screenerService,
                              ScanHistoryService scanHistoryService,
                              ScheduledScanService scheduledScanService) {
        this.screenerService = screenerService;
        this.scanHistoryService = scanHistoryService;
        this.scheduledScanService = scheduledScanService;
    }

    @GetMapping("/trojpolowka")
    @Operation(summary = "Skan trojpolowkowy z konfigurowalnymi filtrami")
    public ResponseEntity<TrojpolowkaResponse> runTrojpolowka(
            @Parameter(description = "Wlacz GPW") @RequestParam(defaultValue = "true") boolean gpw,
            @Parameter(description = "Wlacz US") @RequestParam(defaultValue = "true") boolean us,
            @Parameter(description = "Pary forex (comma-separated, puste=brak)") @RequestParam(defaultValue = "") String forexPairs,
            @Parameter(description = "Dni do avg turnover") @RequestParam(defaultValue = "10") int turnoverDays,
            @Parameter(description = "Min avg turnover dzienny") @RequestParam(defaultValue = "500000") double minTurnover,
            @Parameter(description = "Top N plynnych US (max 500)") @RequestParam(defaultValue = "200") int usTopN) {

        List<String> fxList = new ArrayList<>();
        if (!forexPairs.isBlank()) {
            fxList = Arrays.stream(forexPairs.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .toList();
        }

        int clampedUsTopN = Math.min(Math.max(usTopN, 0), 500);

        TrojpolowkaResponse response = screenerService.runTrojpolowkaScan(
                gpw, us, fxList, turnoverDays, minTurnover, clampedUsTopN);

        scanHistoryService.saveAndCompare(response, gpw, us, forexPairs);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/last-scan")
    @Operation(summary = "Ostatni zapisany skan z bazy danych")
    public ResponseEntity<TrojpolowkaResponse> lastScan() {
        return scanHistoryService.getLastScan()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/forex-groups")
    @Operation(summary = "Dostepne grupy forex")
    public ResponseEntity<Map<String, List<String>>> forexGroups() {
        return ResponseEntity.ok(Map.of(
                "major", ScreenerService.FOREX_MAJOR,
                "minor", ScreenerService.FOREX_MINOR,
                "pln", ScreenerService.FOREX_PLN
        ));
    }

    @GetMapping("/diagnose/{symbol}")
    @Operation(summary = "Diagnostyka ichimoku dla pojedynczego symbolu")
    public ResponseEntity<Map<String, Object>> diagnose(
            @PathVariable String symbol) {
        return ResponseEntity.ok(screenerService.diagnoseSymbol(symbol));
    }

    @GetMapping("/chart-data/{symbol}")
    @Operation(summary = "Dane do wykresu ichimoku (swieczki + linie ichimoku)")
    public ResponseEntity<ChartDataResponse> chartData(@PathVariable String symbol) {
        ChartDataResponse data = screenerService.getChartData(symbol);
        if (data == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(data);
    }

    @GetMapping("/scan-status")
    @Operation(summary = "Status zaplanowanych skanow (ostatni czas, nastepny trigger, bledy)")
    public ResponseEntity<Map<String, Object>> scanStatus() {
        return ResponseEntity.ok(scheduledScanService.getScanStatus());
    }

    @PostMapping("/trigger-scan")
    @Operation(summary = "Reczne uruchomienie pelnego skanu (Ichimoku + Fibonacci)")
    public ResponseEntity<Map<String, Object>> triggerScan() {
        return ResponseEntity.ok(scheduledScanService.triggerManualScan());
    }
}
