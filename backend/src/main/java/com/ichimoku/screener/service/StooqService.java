package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.StockCandle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class StooqService implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(StooqService.class);
    private static final String BASE_URL = "https://stooq.com/q/d/l/";
    private static final DateTimeFormatter STOOQ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1500, 3000, 6000};

    @Override
    public String getName() {
        return "stooq";
    }

    private static final SSLContext TRUST_ALL_SSL;
    static {
        try {
            TRUST_ALL_SSL = SSLContext.getInstance("TLS");
            TRUST_ALL_SSL.init(null, new TrustManager[]{new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }}, new java.security.SecureRandom());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<StockCandle> downloadHistory(String symbol, int daysBack) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays((long) (daysBack * 1.6));

        String url = BASE_URL + "?s=" + symbol + "&d1=" + start.format(STOOQ_DATE)
                + "&d2=" + end.format(STOOQ_DATE) + "&i=d";

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (attempt > 0) {
                    long delay = RETRY_DELAYS_MS[Math.min(attempt - 1, RETRY_DELAYS_MS.length - 1)];
                    log.debug("Retry {} for {} after {}ms", attempt, symbol, delay);
                    Thread.sleep(delay);
                }

                List<StockCandle> candles = doDownload(url, symbol);
                if (candles != null) return candles;

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new ArrayList<>();
            } catch (Exception e) {
                log.warn("Attempt {}/{} for {}: {}", attempt + 1, MAX_RETRIES + 1, symbol, e.getMessage());
                if (attempt == MAX_RETRIES) {
                    log.error("All retries exhausted for {}", symbol);
                }
            }
        }
        return new ArrayList<>();
    }

    private List<StockCandle> doDownload(String url, String symbol) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        if (conn instanceof HttpsURLConnection httpsConn) {
            httpsConn.setSSLSocketFactory(TRUST_ALL_SSL.getSocketFactory());
            httpsConn.setHostnameVerifier((h, s) -> true);
        }
        conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "text/csv,*/*");
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);

        int status = conn.getResponseCode();
        if (status == 429 || status >= 500) {
            log.warn("Stooq {} for {} - will retry", status, symbol);
            conn.disconnect();
            return null;
        }
        if (status != 200) {
            log.warn("Stooq returned {} for {}", status, symbol);
            conn.disconnect();
            return new ArrayList<>();
        }

        List<StockCandle> candles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String header = reader.readLine();
            if (header == null || !header.toLowerCase().contains("date")) {
                return new ArrayList<>();
            }

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.toLowerCase().contains("no data")) continue;

                String[] parts = line.split(",");
                if (parts.length < 5) continue;

                try {
                    LocalDate date = LocalDate.parse(parts[0].trim());
                    double open = Double.parseDouble(parts[1].trim());
                    double high = Double.parseDouble(parts[2].trim());
                    double low = Double.parseDouble(parts[3].trim());
                    double close = Double.parseDouble(parts[4].trim());
                    long volume = parts.length > 5 ? parseLongSafe(parts[5].trim()) : 0;
                    candles.add(new StockCandle(date, open, high, low, close, volume));
                } catch (Exception e) {
                    log.trace("Skip line for {}: {}", symbol, line);
                }
            }
        }

        candles.sort(Comparator.comparing(StockCandle::getDate));
        log.info("Downloaded {} candles for {}", candles.size(), symbol);
        return candles;
    }

    private long parseLongSafe(String s) {
        try {
            return (long) Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
