package com.ichimoku.screener.controller;

import com.ichimoku.screener.dto.FibChartData;
import com.ichimoku.screener.dto.FibonacciResponse;
import com.ichimoku.screener.service.FibScanHistoryService;
import com.ichimoku.screener.service.FibonacciService;
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
@RequestMapping("/api/fibonacci")
@Tag(name = "Fibonacci", description = "Fibonacci impulse retracement screener")
public class FibonacciController {

    private final FibonacciService fibonacciService;
    private final FibScanHistoryService historyService;

    public FibonacciController(FibonacciService fibonacciService, FibScanHistoryService historyService) {
        this.fibonacciService = fibonacciService;
        this.historyService = historyService;
    }

    @GetMapping("/scan")
    @Operation(summary = "Skan Fibonacci - impulsy i retracementy")
    public ResponseEntity<FibonacciResponse> runScan(
            @Parameter(description = "Wlacz GPW") @RequestParam(defaultValue = "true") boolean gpw,
            @Parameter(description = "Wlacz US") @RequestParam(defaultValue = "true") boolean us,
            @Parameter(description = "Pary forex (comma-separated)") @RequestParam(defaultValue = "") String forexPairs,
            @Parameter(description = "Dni do avg turnover") @RequestParam(defaultValue = "10") int turnoverDays,
            @Parameter(description = "Min avg turnover dzienny") @RequestParam(defaultValue = "500000") double minTurnover,
            @Parameter(description = "Top N US") @RequestParam(defaultValue = "200") int usTopN) {

        List<String> fxList = new ArrayList<>();
        if (!forexPairs.isBlank()) {
            fxList = Arrays.stream(forexPairs.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toLowerCase)
                    .toList();
        }

        int clampedUsTopN = Math.min(Math.max(usTopN, 0), 500);

        FibonacciResponse response = fibonacciService.runScan(gpw, us, fxList,
                turnoverDays, minTurnover, clampedUsTopN);

        historyService.saveAndCompare(response, gpw, us, forexPairs);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/last-scan")
    @Operation(summary = "Ostatni zapisany skan Fibonacci z bazy danych")
    public ResponseEntity<FibonacciResponse> lastScan() {
        return historyService.getLastScan()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/chart-data/{symbol}")
    @Operation(summary = "Dane do wykresu Fibonacci (swieczki + poziomy fib)")
    public ResponseEntity<FibChartData> chartData(@PathVariable String symbol) {
        FibChartData data = fibonacciService.getChartData(symbol);
        if (data == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(data);
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
}
