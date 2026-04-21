package com.ichimoku.screener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ichimoku.screener.dto.StockCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Service
public class YahooFinanceService implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceService.class);
    private static final String CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_RETRIES = 2;

    private static final Set<String> FOREX_PAIRS = Set.of(
            "eurusd", "usdjpy", "gbpusd", "usdchf", "audusd", "usdcad", "nzdusd",
            "cadjpy", "nzdjpy", "audjpy", "eurjpy", "cadchf", "chfjpy", "nzdchf",
            "eurgbp", "audchf", "gbpjpy", "eurchf", "audnzd", "nzdcad", "gbpchf",
            "eurcad", "euraud", "gbpnzd", "gbpaud", "eurnzd", "gbpcad",
            "usdpln", "eurpln", "gbppln", "chfpln", "cadpln", "audpln"
    );

    private volatile String crumb;
    private volatile String cookie;

    @Override
    public String getName() {
        return "yahoo";
    }

    @Override
    public List<StockCandle> downloadHistory(String symbol, int daysBack) {
        String yahooSymbol = mapSymbol(symbol);
        if (yahooSymbol == null) return new ArrayList<>();

        long period2 = Instant.now().getEpochSecond();
        long period1 = Instant.now().minus(Duration.ofDays((long) (daysBack * 1.6))).getEpochSecond();

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                ensureAuth();
                String url = buildChartUrl(yahooSymbol, period1, period2);

                List<StockCandle> candles = doDownload(url, symbol, yahooSymbol);
                if (candles != null && !candles.isEmpty()) {
                    log.info("Yahoo downloaded {} candles for {} ({})", candles.size(), symbol, yahooSymbol);
                    return candles;
                }

                if (attempt < MAX_RETRIES) {
                    invalidateAuth();
                    Thread.sleep(1500);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new ArrayList<>();
            } catch (Exception e) {
                log.warn("Yahoo attempt {}/{} for {} ({}): {}",
                        attempt + 1, MAX_RETRIES + 1, symbol, yahooSymbol, e.getMessage());
                if (attempt < MAX_RETRIES) invalidateAuth();
            }
        }
        return new ArrayList<>();
    }

    private String buildChartUrl(String yahooSymbol, long period1, long period2) {
        String url = CHART_URL + URLEncoder.encode(yahooSymbol, StandardCharsets.UTF_8)
                + "?period1=" + period1
                + "&period2=" + period2
                + "&interval=1d&events=history";
        if (crumb != null) {
            url += "&crumb=" + URLEncoder.encode(crumb, StandardCharsets.UTF_8);
        }
        return url;
    }

    private synchronized void ensureAuth() throws Exception {
        if (crumb != null && cookie != null) return;

        HttpURLConnection conn = (HttpURLConnection) URI.create("https://fc.yahoo.com").toURL().openConnection();
        conn.setRequestProperty("User-Agent", userAgent());
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);

        try {
            conn.getResponseCode();
        } catch (Exception ignored) {
        }

        String setCookie = conn.getHeaderField("Set-Cookie");
        conn.disconnect();

        if (setCookie == null) {
            log.warn("Yahoo: no cookie received from fc.yahoo.com");
            return;
        }
        cookie = setCookie.split(";")[0];

        HttpURLConnection crumbConn = (HttpURLConnection) URI.create(CRUMB_URL).toURL().openConnection();
        crumbConn.setRequestProperty("User-Agent", userAgent());
        crumbConn.setRequestProperty("Cookie", cookie);
        crumbConn.setConnectTimeout(10_000);
        crumbConn.setReadTimeout(10_000);

        if (crumbConn.getResponseCode() == 200) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(crumbConn.getInputStream()))) {
                crumb = reader.readLine();
                log.debug("Yahoo crumb acquired");
            }
        } else {
            log.warn("Yahoo crumb request returned {}", crumbConn.getResponseCode());
        }
        crumbConn.disconnect();
    }

    private void invalidateAuth() {
        crumb = null;
        cookie = null;
    }

    private List<StockCandle> doDownload(String url, String symbol, String yahooSymbol) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", userAgent());
        conn.setRequestProperty("Accept", "application/json");
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(20_000);

        int status = conn.getResponseCode();
        if (status == 401 || status == 403) {
            log.warn("Yahoo auth error {} for {} - will reset crumb", status, yahooSymbol);
            invalidateAuth();
            conn.disconnect();
            return null;
        }
        if (status != 200) {
            log.warn("Yahoo returned {} for {}", status, yahooSymbol);
            conn.disconnect();
            return new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        return parseChartJson(sb.toString(), symbol);
    }

    private List<StockCandle> parseChartJson(String json, String symbol) {
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode error = root.path("chart").path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                log.warn("Yahoo chart error for {}: {}", symbol, error);
                return new ArrayList<>();
            }

            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) return new ArrayList<>();

            JsonNode data = result.get(0);
            JsonNode timestamps = data.path("timestamp");
            if (!timestamps.isArray() || timestamps.isEmpty()) return new ArrayList<>();

            JsonNode quote = data.path("indicators").path("quote").get(0);
            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            List<StockCandle> candles = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                try {
                    if (closes.get(i) == null || closes.get(i).isNull()) continue;

                    long epoch = timestamps.get(i).asLong();
                    LocalDate date = Instant.ofEpochSecond(epoch)
                            .atZone(ZoneId.of("UTC")).toLocalDate();
                    double open = safeDouble(opens, i);
                    double high = safeDouble(highs, i);
                    double low = safeDouble(lows, i);
                    double close = closes.get(i).asDouble();
                    long volume = safeVolume(volumes, i);

                    if (close > 0) {
                        candles.add(new StockCandle(date, open, high, low, close, volume));
                    }
                } catch (Exception e) {
                    log.trace("Yahoo skip candle {} for {}: {}", i, symbol, e.getMessage());
                }
            }

            candles.sort(Comparator.comparing(StockCandle::getDate));
            return candles;
        } catch (Exception e) {
            log.error("Failed to parse Yahoo JSON for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    static String mapSymbol(String stooqSymbol) {
        if (stooqSymbol == null) return null;
        String s = stooqSymbol.toLowerCase().trim();

        if (s.endsWith(".us")) {
            return s.substring(0, s.length() - 3).toUpperCase();
        }

        if (FOREX_PAIRS.contains(s)) {
            return s.toUpperCase() + "=X";
        }

        return s.toUpperCase() + ".WA";
    }

    private static double safeDouble(JsonNode array, int i) {
        JsonNode node = array.get(i);
        return (node == null || node.isNull()) ? 0 : node.asDouble();
    }

    private static long safeVolume(JsonNode array, int i) {
        JsonNode node = array.get(i);
        return (node == null || node.isNull()) ? 0 : node.asLong();
    }

    private static String userAgent() {
        return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    }
}
