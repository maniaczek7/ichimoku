package com.ichimoku.screener.controller;

import com.ichimoku.screener.dto.BacktestResult;
import com.ichimoku.screener.service.FibBacktestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/fibonacci/backtest")
@Tag(name = "Fibonacci Backtest", description = "Backtest strategii Fibonacci bounce")
public class FibBacktestController {

    private final FibBacktestService fibBacktestService;

    public FibBacktestController(FibBacktestService fibBacktestService) {
        this.fibBacktestService = fibBacktestService;
    }

    @GetMapping
    @Operation(summary = "Backtest strategii Fib bounce — entry na 61.8%, SL pod patternem, TP na 100%")
    public ResponseEntity<BacktestResult> runBacktest(
            @Parameter(description = "Wlacz GPW") @RequestParam(defaultValue = "true") boolean gpw,
            @Parameter(description = "Wlacz US") @RequestParam(defaultValue = "true") boolean us,
            @Parameter(description = "Pary forex") @RequestParam(defaultValue = "") String forexPairs,
            @Parameter(description = "Top N US (max 500)") @RequestParam(defaultValue = "200") int usTopN,
            @Parameter(description = "Data od") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Data do") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        List<String> fxList = new ArrayList<>();
        if (!forexPairs.isBlank()) {
            fxList = Arrays.stream(forexPairs.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(String::toLowerCase).toList();
        }

        int clampedUs = Math.min(Math.max(usTopN, 0), 500);
        BacktestResult result = fibBacktestService.run(gpw, us, fxList, clampedUs, dateFrom, dateTo);
        return ResponseEntity.ok(result);
    }
}
