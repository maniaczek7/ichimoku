package com.ichimoku.screener.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ichimoku.screener.dto.StockCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class TwelveDataService implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataService.class);
    private static final String BASE_URL = "https://api.twelvedata.com/time_series";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 2000;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int MAX_REQUESTS_PER_MINUTE = 7;
    private static final long WINDOW_MS = 60_000;

    private final Deque<Long> requestTimestamps = new ConcurrentLinkedDeque<>();
    private final String apiKey;

    public TwelveDataService(@Value("${screener.twelvedata.api-key:}") String apiKey) {
        this.apiKey = apiKey;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("TwelveData API key not configured (screener.twelvedata.api-key)");
        } else {
            log.info("TwelveData provider initialized (rate limit: {}/min)", MAX_REQUESTS_PER_MINUTE);
        }
    }

    @Override
    public String getName() {
        return "twelvedata";
    }

    @Override
    public List<StockCandle> downloadHistory(String symbol, int daysBack) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("TwelveData API key not set, skipping {}", symbol);
            return new ArrayList<>();
        }

        String twelveSymbol = mapSymbol(symbol);
        int outputSize = Math.min((int) (daysBack * 1.6), 5000);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    Thread.sleep(RETRY_DELAY_MS * attempt);
                }

                waitForRateLimit(symbol);

                String url = buildUrl(twelveSymbol, outputSize);
                List<StockCandle> candles = doDownload(url, symbol, twelveSymbol);

                if (candles != null && !candles.isEmpty()) {
                    log.info("TwelveData downloaded {} candles for {} ({})",
                            candles.size(), symbol, twelveSymbol);
                    return candles;
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new ArrayList<>();
            } catch (Exception e) {
                log.warn("TwelveData attempt {}/{} for {} ({}): {}",
                        attempt + 1, MAX_RETRIES + 1, symbol, twelveSymbol, e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    private synchronized void waitForRateLimit(String symbol) throws InterruptedException {
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_MS;

        while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < windowStart) {
            requestTimestamps.pollFirst();
        }

        if (requestTimestamps.size() >= MAX_REQUESTS_PER_MINUTE) {
            long oldestInWindow = requestTimestamps.peekFirst();
            long sleepMs = oldestInWindow + WINDOW_MS - now + 500;
            if (sleepMs > 0) {
                log.debug("TwelveData rate limit: waiting {}ms before requesting {}", sleepMs, symbol);
                Thread.sleep(sleepMs);
            }
            while (!requestTimestamps.isEmpty() && requestTimestamps.peekFirst() < System.currentTimeMillis() - WINDOW_MS) {
                requestTimestamps.pollFirst();
            }
        }

        requestTimestamps.addLast(System.currentTimeMillis());
    }

    private String buildUrl(String twelveSymbol, int outputSize) {
        return BASE_URL
                + "?symbol=" + URLEncoder.encode(twelveSymbol, StandardCharsets.UTF_8)
                + "&interval=1day"
                + "&outputsize=" + outputSize
                + "&order=asc"
                + "&timezone=UTC"
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private List<StockCandle> doDownload(String url, String symbol, String twelveSymbol) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestProperty("User-Agent", "IchimokuScreener/1.0");
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(20_000);
        conn.setReadTimeout(20_000);

        int status = conn.getResponseCode();
        if (status == 429) {
            log.warn("TwelveData rate limited (429) for {} - will retry", twelveSymbol);
            conn.disconnect();
            return null;
        }
        if (status != 200) {
            log.warn("TwelveData returned {} for {}", status, twelveSymbol);
            conn.disconnect();
            return new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        conn.disconnect();

        return parseResponse(sb.toString(), symbol);
    }

    private List<StockCandle> parseResponse(String json, String symbol) {
        try {
            JsonNode root = mapper.readTree(json);

            if (root.has("code") && root.get("code").asInt() != 200) {
                String message = root.has("message") ? root.get("message").asText() : "unknown error";
                log.warn("TwelveData API error for {}: {}", symbol, message);
                return new ArrayList<>();
            }

            if (root.has("status") && "error".equals(root.get("status").asText())) {
                String message = root.has("message") ? root.get("message").asText() : "unknown error";
                log.warn("TwelveData error for {}: {}", symbol, message);
                return new ArrayList<>();
            }

            JsonNode values = root.path("values");
            if (!values.isArray() || values.isEmpty()) {
                log.warn("TwelveData returned no values for {}", symbol);
                return new ArrayList<>();
            }

            List<StockCandle> candles = new ArrayList<>();
            for (JsonNode point : values) {
                try {
                    String dateStr = point.get("datetime").asText();
                    LocalDate date = LocalDate.parse(dateStr.substring(0, 10), DATE_FMT);
                    double open = Double.parseDouble(point.get("open").asText());
                    double high = Double.parseDouble(point.get("high").asText());
                    double low = Double.parseDouble(point.get("low").asText());
                    double close = Double.parseDouble(point.get("close").asText());
                    long volume = parseVolume(point.get("volume"));

                    if (close > 0) {
                        candles.add(new StockCandle(date, open, high, low, close, volume));
                    }
                } catch (Exception e) {
                    log.trace("TwelveData skip point for {}: {}", symbol, e.getMessage());
                }
            }

            candles.sort(Comparator.comparing(StockCandle::getDate));
            return candles;
        } catch (Exception e) {
            log.error("Failed to parse TwelveData JSON for {}: {}", symbol, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Maps internal symbol format (stooq-style) to Twelve Data format.
     * eurusd -> EUR/USD, usdjpy -> USD/JPY, etc.
     */
    static String mapSymbol(String symbol) {
        if (symbol == null) return null;
        String s = symbol.toLowerCase().trim();

        if (s.length() == 6 && s.chars().allMatch(Character::isLetter)) {
            String base = s.substring(0, 3).toUpperCase();
            String quote = s.substring(3).toUpperCase();
            return base + "/" + quote;
        }

        if (s.endsWith(".us")) {
            return s.substring(0, s.length() - 3).toUpperCase();
        }

        return s.toUpperCase();
    }

    private static long parseVolume(JsonNode node) {
        if (node == null || node.isNull()) return 0;
        try {
            return (long) Double.parseDouble(node.asText());
        } catch (Exception e) {
            return 0;
        }
    }
}
