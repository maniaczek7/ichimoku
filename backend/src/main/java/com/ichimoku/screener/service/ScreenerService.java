package com.ichimoku.screener.service;

import com.ichimoku.screener.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class ScreenerService {

    private static final Logger log = LoggerFactory.getLogger(ScreenerService.class);
    private static final int FOUR_MONTHS_DAYS = 84;
    private static final int HISTORY_DAYS = 400;
    private static final int DISPLACEMENT = 26;
    private static final double CLOUD_EDGE_TOLERANCE = 0;
    private static final int GRACE_DAYS = 0;

    private final MarketDataService marketDataService;
    private final IchimokuCalculator ichimokuCalc;
    private final CandlePatternDetector patternDetector;

    public static final List<String> GPW_SYMBOLS = List.of(
            "ale", "alr", "acp", "bnp", "ccc", "cdr", "cps", "dnp",
            "jsw", "kgh", "kru", "kty", "lpp", "mbk", "opl", "peo",
            "pge", "pkn", "pko", "pzu", "spl", "xtb", "pco", "ing",
            "gpw", "dom", "mil", "krk", "mrc", "tpe", "eur",
            "vox", "snk", "pcr", "txt", "wpl", "ker",
            "ato", "bft", "car", "ena", "ten", "neu", "ply",
            "mab", "grn", "dbc", "cig", "mlg", "pkp", "hdr"
    );

    public static final List<String> US_SYMBOLS = List.of(
            // ── mega cap ──
            "aapl.us", "msft.us", "amzn.us", "googl.us", "meta.us", "nvda.us", "tsla.us", "brk-b.us",
            // ── tech ──
            "avgo.us", "adbe.us", "crm.us", "csco.us", "acn.us", "orcl.us", "ibm.us", "txn.us",
            "qcom.us", "amd.us", "intc.us", "amat.us", "mu.us", "lrcx.us", "klac.us", "snps.us",
            "cdns.us", "mchp.us", "nxpi.us", "on.us", "swks.us", "mpwr.us", "ftnt.us", "panw.us",
            "crwd.us", "zs.us", "net.us", "ddog.us", "snow.us", "pltr.us", "now.us", "adp.us",
            "ctsh.us", "it.us", "cdw.us", "hpe.us", "keys.us", "tyl.us", "gddy.us",
            "ttd.us", "intu.us", "fisv.us", "gr.us", "payc.us", "wday.us", "mdb.us",
            "team.us", "rng.us", "twlo.us",
            // ── finance ──
            "jpm.us", "v.us", "ma.us", "bac.us", "wfc.us", "gs.us", "ms.us", "spgi.us", "blk.us",
            "axp.us", "c.us", "schw.us", "cb.us", "aon.us", "ice.us", "cme.us",
            "pnc.us", "tfc.us", "usb.us", "aig.us", "met.us", "pru.us", "all.us", "trv.us",
            "afl.us", "bk.us", "cof.us", "hig.us", "cinf.us", "msci.us", "mktx.us",
            "cboe.us", "ndaq.us",
            // ── healthcare ──
            "unh.us", "jnj.us", "lly.us", "abbv.us", "mrk.us", "pfe.us", "tmo.us", "abt.us",
            "dhr.us", "bmy.us", "amgn.us", "gild.us", "mdt.us", "elv.us", "ci.us", "isrg.us",
            "regn.us", "vrtx.us", "bsx.us", "zts.us", "bdx.us", "syk.us", "idxx.us", "dxcm.us",
            "algn.us", "holx.us", "ilmn.us", "a.us", "wst.us", "mtd.us", "bio.us", "tech.us",
            "rvty.us", "hsic.us",
            // ── consumer ──
            "pg.us", "ko.us", "pep.us", "cost.us", "wmt.us", "hd.us", "mcd.us", "nke.us",
            "sbux.us", "tgt.us", "low.us", "tjx.us", "dg.us", "dltr.us", "rost.us", "orly.us",
            "azo.us", "bby.us", "ebay.us", "etsy.us", "cl.us", "kmb.us", "mnst.us", "mdlz.us",
            "gis.us", "stz.us", "tap.us", "hsy.us", "mkc.us", "elf.us",
            "pool.us", "gpc.us", "fast.us", "uri.us", "lkq.us",
            // ── communication / media ──
            "nflx.us", "dis.us", "cmcsa.us", "t.us", "vz.us", "chtr.us", "tmus.us",
            "spot.us", "pins.us", "snap.us", "roku.us",
            // ── industrial ──
            "cat.us", "ge.us", "ba.us", "hon.us", "rtx.us", "ups.us", "unp.us", "de.us",
            "mmm.us", "lmt.us", "gd.us", "noc.us", "itw.us", "etn.us", "pcar.us", "emr.us",
            "rop.us", "csx.us", "nsc.us", "fdx.us", "wr.us", "msi.us", "tdy.us", "br.us",
            "pwr.us", "carr.us", "otis.us", "lhx.us", "ldos.us", "j.us", "gev.us",
            // ── energy ──
            "xom.us", "cvx.us", "cop.us", "eog.us", "slb.us", "mpc.us", "psx.us", "vlo.us",
            "oxy.us", "hes.us", "dvn.us", "hal.us", "fang.us", "kmi.us", "wmb.us", "okc.us",
            // ── real estate ──
            "amt.us", "pld.us", "cci.us", "eqix.us", "o.us", "dlr.us", "well.us", "avb.us",
            "eqr.us", "sbac.us", "are.us", "psa.us", "spg.us", "vici.us", "maa.us",
            // ── utilities ──
            "nee.us", "duk.us", "so.us", "d.us", "aep.us", "exc.us", "sre.us", "ed.us",
            "wec.us", "es.us", "dte.us", "peg.us", "fe.us", "xel.us", "etr.us", "cms.us",
            "awk.us", "lnt.us",
            // ── materials ──
            "lin.us", "apd.us", "shw.us", "ecl.us", "fcx.us", "nem.us", "nue.us", "dd.us",
            "pkg.us", "ip.us", "cf.us", "mos.us", "alb.us", "ctva.us",
            // ── travel / leisure ──
            "bkng.us", "abnb.us", "hlt.us", "mar.us", "rcl.us", "ccl.us", "expe.us",
            "dash.us", "uber.us", "lyft.us",
            // ── auto ──
            "f.us", "gm.us", "rivn.us",
            // ── misc ──
            "pypl.us", "sq.us"
    );

    public static final List<String> FOREX_MAJOR = List.of(
            "eurusd", "usdjpy", "gbpusd", "usdchf", "audusd",
            "usdcad", "nzdusd"
    );

    public static final List<String> FOREX_MINOR = List.of(
            "cadjpy", "nzdjpy", "audjpy", "eurjpy", "cadchf", "chfjpy", "nzdchf",
            "eurgbp", "audchf", "gbpjpy", "eurchf", "audnzd", "nzdcad", "gbpchf",
            "eurcad", "euraud", "gbpnzd", "gbpaud", "eurnzd", "gbpcad"
    );

    public static final List<String> FOREX_PLN = List.of(
            "usdpln", "eurpln", "gbppln", "chfpln", "cadpln", "audpln"
    );

    public ScreenerService(MarketDataService marketDataService, IchimokuCalculator ichimokuCalc,
                           CandlePatternDetector patternDetector) {
        this.marketDataService = marketDataService;
        this.ichimokuCalc = ichimokuCalc;
        this.patternDetector = patternDetector;
    }

    // ───────── TROJPOLOWKA SCAN ─────────

    public TrojpolowkaResponse runTrojpolowkaScan(boolean includeGpw, boolean includeUs,
                                                   List<String> forexPairs,
                                                   int turnoverDays, double minTurnover, int usTopN) {
        marketDataService.resetForNewScan();
        TrojpolowkaResponse response = new TrojpolowkaResponse();
        List<TrojpolowkaEntry> allEntries = Collections.synchronizedList(new ArrayList<>());
        List<AnalysisLogEntry> analysisLog = Collections.synchronizedList(new ArrayList<>());

        List<String[]> allSymbols = new ArrayList<>();
        if (includeGpw) GPW_SYMBOLS.forEach(s -> allSymbols.add(new String[]{s, "GPW"}));
        if (includeUs) US_SYMBOLS.stream().limit(usTopN).forEach(s -> allSymbols.add(new String[]{s, "US"}));
        if (forexPairs != null) {
            forexPairs.forEach(s -> allSymbols.add(new String[]{s, "FOREX"}));
        }

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        for (String[] sym : allSymbols) {
            futures.add(executor.submit(() -> {
                try {
                    Thread.sleep(200);
                    TrojpolowkaEntry result = analyzeTrojpolowka(sym[0], sym[1], turnoverDays, minTurnover);
                    if (result != null) {
                        allEntries.add(result);
                        analysisLog.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "OK",
                                result.getDirection() + " " + result.getCloudDays() + "d"));
                    } else {
                        analysisLog.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "NO_SIGNAL", ""));
                    }
                } catch (Exception e) {
                    log.warn("Error analyzing {}: {}", sym[0], e.getMessage());
                    analysisLog.add(new AnalysisLogEntry(sym[0].toUpperCase(), sym[1], "ERROR", e.getMessage()));
                }
            }));
        }

        for (Future<?> f : futures) {
            try { f.get(90, TimeUnit.SECONDS); } catch (Exception e) { /* skip */ }
        }
        executor.shutdown();

        for (TrojpolowkaEntry entry : allEntries) {
            int col = classifyColumn(entry);
            switch (col) {
                case 1 -> response.getColumn1().add(entry);
                case 2 -> response.getColumn2().add(entry);
                case 3 -> response.getColumn3().add(entry);
            }
        }

        response.getColumn1().sort(Comparator.comparingInt(TrojpolowkaEntry::getCloudDays).reversed());
        response.getColumn2().sort(Comparator.comparingInt(TrojpolowkaEntry::getDaysSinceTkCross));
        response.getColumn3().sort(
                Comparator.comparing((TrojpolowkaEntry e) -> {
                    if ("IN_PLAY".equals(e.getCol3Status())) return 0;
                    if ("IN_CLOUD".equals(e.getCol3Status())) return 1;
                    return 2;
                })
                .thenComparing(Comparator.comparingInt(TrojpolowkaEntry::getScore).reversed())
                .thenComparing(Comparator.comparingInt(TrojpolowkaEntry::getBreakoutDaysAgo)));
        response.setLog(analysisLog);
        response.setDataSource(marketDataService.getActiveProviderName());

        return response;
    }

    private int classifyColumn(TrojpolowkaEntry entry) {
        if (entry.getDirection() == null) return 0;

        boolean has4Months = entry.getCloudDays() >= FOUR_MONTHS_DAYS;
        boolean tkInPosition = entry.isTkSwapped();
        boolean priceInCloud = "IN".equals(entry.getCloudPosition());
        boolean breakout = entry.isBreakoutDetected();

        if (!has4Months) return 0;

        if (breakout) {
            entry.setCol3Status("IN_PLAY");
            return 3;
        }
        if (priceInCloud) {
            entry.setCol3Status("IN_CLOUD");
            return 3;
        }

        // Price still on original side of the cloud
        if (tkInPosition) return 2;
        return 1;
    }

    // ───────── SINGLE SYMBOL ANALYSIS ─────────

    private TrojpolowkaEntry analyzeTrojpolowka(String symbol, String market,
                                                 int turnoverDays, double minTurnover) {
        List<StockCandle> candles = marketDataService.downloadHistory(symbol, HISTORY_DAYS);
        if (candles.size() < 100) return null;

        boolean isForex = "FOREX".equals(market);
        double avgTurnover = 0;
        if (!isForex) {
            avgTurnover = calcAvgTurnover(candles, turnoverDays);
            if (avgTurnover < minTurnover) return null;
        }

        List<IchimokuValues> ichimoku = ichimokuCalc.calculate(candles);
        double[][] futureCloud = ichimokuCalc.getFutureCloud(candles);

        int startIdx = findFirstFullCloudIndex(ichimoku);
        if (startIdx < 0 || startIdx >= candles.size()) return null;

        int size = candles.size();
        TrojpolowkaEntry entry = new TrojpolowkaEntry();
        entry.setSymbol(symbol.toUpperCase());
        entry.setMarket(market);
        entry.setLastClose(candles.get(size - 1).getClose());
        entry.setAvgTurnover10d(avgTurnover);

        analyzeSetup(candles, ichimoku, startIdx, entry);
        if (entry.getDirection() == null) return null;

        analyzeTkCross(ichimoku, entry);
        analyzeChikou(candles, entry);
        analyzeKumoTwist(futureCloud, entry);
        calculateScore(entry);
        buildSignals(entry);

        return entry;
    }

    /**
     * LONG: price was below cloudTop for N consecutive days, breakout = close > cloudTop.
     * SHORT: price was above cloudBottom for N consecutive days, breakout = close < cloudBottom.
     * Also detects setups with < 4 months (direction set, unplayableBreakout flag).
     */
    private void analyzeSetup(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                               int startIdx, TrojpolowkaEntry entry) {
        int size = candles.size();
        double lastClose = candles.get(size - 1).getClose();
        IchimokuValues lastIchi = ichimoku.get(size - 1);
        double cloudTop = lastIchi.getCloudTop();
        double cloudBottom = lastIchi.getCloudBottom();

        if (cloudTop == 0 || cloudBottom == 0) return;

        entry.setCloudTopValue(cloudTop);
        entry.setCloudBottomValue(cloudBottom);

        double topTol = cloudTop * CLOUD_EDGE_TOLERANCE;
        double botTol = cloudBottom * CLOUD_EDGE_TOLERANCE;
        boolean rawAbove = lastClose > cloudTop;
        boolean rawBelow = lastClose < cloudBottom;
        boolean aboveCloud = lastClose > cloudTop + topTol;
        boolean belowCloud = lastClose < cloudBottom - botTol;
        entry.setCloudPosition(aboveCloud ? "ABOVE" : belowCloud ? "UNDER" : "IN");
        entry.setNearCloudEdge((rawAbove && !aboveCloud) || (rawBelow && !belowCloud));

        log.debug("{}: close={} cloudTop={} cloudBottom={} tol={} pos={}",
                entry.getSymbol(), lastClose, cloudTop, cloudBottom, topTol,
                entry.getCloudPosition());

        int longBreakoutDays = 0;
        int longCloudDays = 0;

        if (aboveCloud) {
            longBreakoutDays = countWithGrace(candles, ichimoku, size - 1, startIdx, true, true);
            longCloudDays = countWithGrace(candles, ichimoku, size - 1 - longBreakoutDays, startIdx, true, false);
        } else {
            longCloudDays = countWithGrace(candles, ichimoku, size - 1, startIdx, true, false);
        }

        int shortBreakoutDays = 0;
        int shortCloudDays = 0;

        if (belowCloud) {
            shortBreakoutDays = countWithGrace(candles, ichimoku, size - 1, startIdx, false, false);
            shortCloudDays = countWithGrace(candles, ichimoku, size - 1 - shortBreakoutDays, startIdx, false, true);
        } else {
            shortCloudDays = countWithGrace(candles, ichimoku, size - 1, startIdx, false, true);
        }

        boolean validLong = longCloudDays >= 20;
        boolean validShort = shortCloudDays >= 20;
        boolean longBrk = validLong && longBreakoutDays > 0;
        boolean shortBrk = validShort && shortBreakoutDays > 0;

        if (longBrk && !shortBrk) {
            setDirection(entry, "LONG", longCloudDays, longBreakoutDays);
        } else if (shortBrk && !longBrk) {
            setDirection(entry, "SHORT", shortCloudDays, shortBreakoutDays);
        } else if (longBrk) {
            if (longCloudDays >= shortCloudDays) setDirection(entry, "LONG", longCloudDays, longBreakoutDays);
            else setDirection(entry, "SHORT", shortCloudDays, shortBreakoutDays);
        } else if (validLong && !validShort) {
            setDirection(entry, "LONG", longCloudDays, 0);
        } else if (validShort && !validLong) {
            setDirection(entry, "SHORT", shortCloudDays, 0);
        } else if (validLong) {
            if (longCloudDays >= shortCloudDays) setDirection(entry, "LONG", longCloudDays, 0);
            else setDirection(entry, "SHORT", shortCloudDays, 0);
        }
    }

    private void setDirection(TrojpolowkaEntry e, String dir, int days, int breakoutDays) {
        e.setDirection(dir);
        e.setCloudDays(days);
        e.setBreakoutDetected(breakoutDays > 0);
        e.setBreakoutDaysAgo(breakoutDays);
    }

    /**
     * When breakout happened before 4 months: find the opposing signal date (where price first
     * crossed to the other side of the cloud), count retests, and determine playability.
     *
     * Rule: opposing signal = the candle where price closed on the opposite side of the cloud.
     * From that candle we count days. If >= 84 days have passed AND there was a first retest
     * confirming direction, the second retest is playable.
     */
    private void analyzeUnplayableBreakout(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                            int startIdx, TrojpolowkaEntry entry) {
        entry.setUnplayableBreakout(true);
        int size = candles.size();

        int breakoutStart = size - 1 - entry.getBreakoutDaysAgo();
        int opposingSignalIdx = findOpposingSignal(candles, ichimoku, startIdx, breakoutStart, entry.getDirection());

        if (opposingSignalIdx < 0) {
            entry.setDaysSinceOpposingSignal(0);
            return;
        }

        entry.setDaysSinceOpposingSignal(size - 1 - opposingSignalIdx);

        int retests = countRetests(candles, ichimoku, opposingSignalIdx, size - 1, entry.getDirection());
        entry.setRetestCount(retests);
        entry.setRetestConfirmsDirection(retests >= 1);
        entry.setSecondRetestPlayable(retests >= 2 && entry.getDaysSinceOpposingSignal() >= FOUR_MONTHS_DAYS);
    }

    /**
     * Find the opposing signal: the candle where price first crossed to the current side of the cloud,
     * i.e. the "zamek" capture point. We search backwards from the cloud-days period.
     */
    private int findOpposingSignal(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                    int startIdx, int searchEnd, String direction) {
        for (int i = searchEnd; i >= startIdx; i--) {
            if ("LONG".equals(direction)) {
                double ct = validCloudTop(ichimoku, i);
                if (ct <= 0) continue;
                if (candles.get(i).getClose() < ct) {
                    for (int j = i; j >= startIdx; j--) {
                        double ct2 = validCloudTop(ichimoku, j);
                        if (ct2 <= 0) continue;
                        if (candles.get(j).getClose() >= ct2) return j;
                    }
                    return i;
                }
            } else {
                double cb = validCloudBottom(ichimoku, i);
                if (cb <= 0) continue;
                if (candles.get(i).getClose() > cb) {
                    for (int j = i; j >= startIdx; j--) {
                        double cb2 = validCloudBottom(ichimoku, j);
                        if (cb2 <= 0) continue;
                        if (candles.get(j).getClose() <= cb2) return j;
                    }
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Count retests: price exits cloud on the breakout side, returns to cloud edge, then bounces.
     * A retest = price touches or enters the cloud, then moves back in the breakout direction.
     */
    private int countRetests(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                              int from, int to, String direction) {
        int retests = 0;
        boolean outsideCloud = false;

        for (int i = from; i <= to; i++) {
            double ct = validCloudTop(ichimoku, i);
            double cb = validCloudBottom(ichimoku, i);
            if (ct <= 0 || cb <= 0) continue;

            double close = candles.get(i).getClose();

            if ("LONG".equals(direction)) {
                if (close > ct) {
                    if (!outsideCloud) outsideCloud = true;
                } else if (outsideCloud && close <= ct && close >= cb) {
                    outsideCloud = false;
                    retests++;
                } else if (close < cb) {
                    outsideCloud = false;
                }
            } else {
                if (close < cb) {
                    if (!outsideCloud) outsideCloud = true;
                } else if (outsideCloud && close >= cb && close <= ct) {
                    outsideCloud = false;
                    retests++;
                } else if (close > ct) {
                    outsideCloud = false;
                }
            }
        }
        return retests;
    }

    /**
     * Positive Retest for entries without a direction yet (standalone retest detection).
     *
     * Retest rules:
     * - The candle must TOUCH the cloud (wick or body reaches cloud boundary)
     * - The candle may close on the correct side OR inside the cloud
     * - The candle must NOT close on the OPPOSITE side (that would be a breakout)
     * - A recognized price action formation must be present
     */
    private void analyzePositiveRetest(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                        TrojpolowkaEntry entry) {
        int size = candles.size();
        if (size < 5) return;

        IchimokuValues lastIchi = ichimoku.get(size - 1);
        double cloudTop = lastIchi.getCloudTop();
        double cloudBottom = lastIchi.getCloudBottom();
        if (cloudTop == 0 || cloudBottom == 0) return;

        StockCandle lastCandle = candles.get(size - 1);
        double close = lastCandle.getClose();
        double low = lastCandle.getLow();
        double high = lastCandle.getHigh();

        boolean inCloud = close >= cloudBottom && close <= cloudTop;
        boolean closedAboveCloud = close > cloudTop;
        boolean closedBelowCloud = close < cloudBottom;

        boolean touchesCloudFromAbove = low <= cloudTop;
        boolean touchesCloudFromBelow = high >= cloudBottom;

        boolean longCandidate = (closedAboveCloud || inCloud) && !closedBelowCloud && touchesCloudFromAbove;
        boolean shortCandidate = (closedBelowCloud || inCloud) && !closedAboveCloud && touchesCloudFromBelow;

        if (!longCandidate && !shortCandidate) return;

        String pattern = patternDetector.detectPattern(candles);
        if (pattern == null) return;

        if (longCandidate && patternDetector.isBullishPattern(pattern)) {
            entry.setPositiveRetest(true);
            entry.setDetectedPattern(pattern);
            entry.setDirection("LONG");
            entry.setRetestZone(inCloud ? "IN_CLOUD" : "CLOUD_EDGE");
        } else if (shortCandidate && patternDetector.isBearishPattern(pattern)) {
            entry.setPositiveRetest(true);
            entry.setDetectedPattern(pattern);
            entry.setDirection("SHORT");
            entry.setRetestZone(inCloud ? "IN_CLOUD" : "CLOUD_EDGE");
        }
    }

    /**
     * Check for price action retest patterns on existing entries that already have a direction.
     *
     * Same rules: candle touches cloud, closes on correct side or inside, NOT on opposite side.
     */
    private void analyzePatternAtCloud(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                        TrojpolowkaEntry entry) {
        if (entry.isPositiveRetest()) return;

        int size = candles.size();
        IchimokuValues lastIchi = ichimoku.get(size - 1);
        double cloudTop = lastIchi.getCloudTop();
        double cloudBottom = lastIchi.getCloudBottom();
        if (cloudTop == 0 || cloudBottom == 0) return;

        StockCandle lastCandle = candles.get(size - 1);
        double close = lastCandle.getClose();
        double low = lastCandle.getLow();
        double high = lastCandle.getHigh();

        boolean inCloud = close >= cloudBottom && close <= cloudTop;
        boolean closedAboveCloud = close > cloudTop;
        boolean closedBelowCloud = close < cloudBottom;

        boolean isLong = "LONG".equals(entry.getDirection());

        boolean notWrongSide = isLong ? !closedBelowCloud : !closedAboveCloud;
        if (!notWrongSide) return;

        boolean touchesCloud = isLong ? (low <= cloudTop) : (high >= cloudBottom);
        if (!touchesCloud) return;

        boolean onCorrectSideOrInCloud = isLong ? (closedAboveCloud || inCloud) : (closedBelowCloud || inCloud);
        if (!onCorrectSideOrInCloud) return;

        String pattern = patternDetector.detectPattern(candles);
        if (pattern == null) return;

        boolean patternMatchesDir = (isLong && patternDetector.isBullishPattern(pattern))
                || (!isLong && patternDetector.isBearishPattern(pattern));

        if (patternMatchesDir) {
            entry.setPositiveRetest(true);
            entry.setDetectedPattern(pattern);
            entry.setRetestZone(inCloud ? "IN_CLOUD" : "CLOUD_EDGE");
        }
    }

    private void analyzeTkCross(List<IchimokuValues> ichimoku, TrojpolowkaEntry entry) {
        int size = ichimoku.size();

        double nowT = ichimoku.get(size - 1).getTenkanSen();
        double nowK = ichimoku.get(size - 1).getKijunSen();
        if (nowT != 0 && nowK != 0) {
            boolean bullState = nowT > nowK;
            boolean bearState = nowT < nowK;
            entry.setTkSwapped(
                    ("LONG".equals(entry.getDirection()) && bullState)
                            || ("SHORT".equals(entry.getDirection()) && bearState));
        }

        int lookback = Math.min(200, size);
        for (int i = size - 1; i >= size - lookback && i >= 1; i--) {
            double currT = ichimoku.get(i).getTenkanSen();
            double currK = ichimoku.get(i).getKijunSen();
            double prevT = ichimoku.get(i - 1).getTenkanSen();
            double prevK = ichimoku.get(i - 1).getKijunSen();

            if (currT == 0 || currK == 0 || prevT == 0 || prevK == 0) continue;

            boolean bull = prevT <= prevK && currT > currK;
            boolean bear = prevT >= prevK && currT < currK;

            if (bull || bear) {
                entry.setTkCross(true);
                entry.setTkCrossDirection(bull ? "BULLISH" : "BEARISH");
                entry.setDaysSinceTkCross(size - 1 - i);
                return;
            }
        }
        entry.setTkCross(false);
    }

    private void analyzeChikou(List<StockCandle> candles, TrojpolowkaEntry entry) {
        int size = candles.size();
        if (size <= DISPLACEMENT) {
            entry.setChikouConfirms(false);
            return;
        }

        double currentClose = candles.get(size - 1).getClose();
        double priceAt26Back = candles.get(size - 1 - DISPLACEMENT).getClose();

        entry.setChikouValue(currentClose);
        entry.setPriceAtChikou(priceAt26Back);

        if ("LONG".equals(entry.getDirection())) {
            entry.setChikouConfirms(currentClose > priceAt26Back);
        } else {
            entry.setChikouConfirms(currentClose < priceAt26Back);
        }
    }

    private void analyzeKumoTwist(double[][] futureCloud, TrojpolowkaEntry entry) {
        boolean twistFound = false;

        for (int i = 1; i < futureCloud.length; i++) {
            double prevA = futureCloud[i - 1][0];
            double prevB = futureCloud[i - 1][1];
            double currA = futureCloud[i][0];
            double currB = futureCloud[i][1];

            if (currA == 0 || currB == 0 || prevA == 0 || prevB == 0) continue;

            double prevDiff = prevA - prevB;
            double currDiff = currA - currB;

            if (prevDiff * currDiff < 0) {
                boolean bullishTwist = currDiff > 0;
                if ("LONG".equals(entry.getDirection()) && bullishTwist) {
                    twistFound = true;
                    break;
                } else if ("SHORT".equals(entry.getDirection()) && !bullishTwist) {
                    twistFound = true;
                    break;
                }
            }
        }
        entry.setKumoTwist(twistFound);
    }

    private void calculateScore(TrojpolowkaEntry entry) {
        if (!entry.isBreakoutDetected()) {
            entry.setScore(0);
            return;
        }
        int score = 1;
        if (entry.isKumoTwist()) score++;
        if (entry.isChikouConfirms()) score++;
        entry.setScore(score);
    }

    private void buildSignals(TrojpolowkaEntry entry) {
        List<String> signals = entry.getSignals();
        String dir = "LONG".equals(entry.getDirection()) ? "L" : "S";
        String side = "LONG".equals(entry.getDirection()) ? "pod" : "nad";

        signals.add(dir + ": " + entry.getCloudDays() + "d " + side + " chmura");

        if (entry.isUnplayableBreakout()) {
            signals.add("NIEGRYWALNY (<4msc)");
            if (entry.getRetestCount() > 0) {
                signals.add("Retesty: " + entry.getRetestCount());
            }
            if (entry.isRetestConfirmsDirection()) {
                signals.add("1. retest potwierdzil kierunek");
            }
            if (entry.isSecondRetestPlayable()) {
                signals.add("2. retest grywalny (" + entry.getDaysSinceOpposingSignal() + "d od sygn.)");
            } else if (entry.getDaysSinceOpposingSignal() > 0) {
                signals.add(entry.getDaysSinceOpposingSignal() + "d od sygnalu przeciwst.");
            }
        }

        if (entry.isTkCross()) {
            String cross = "BULLISH".equals(entry.getTkCrossDirection()) ? "bycze" : "niedzwiedzie";
            signals.add("TK " + cross + " (" + entry.getDaysSinceTkCross() + "d)");
        }
        if (entry.isBreakoutDetected()) {
            signals.add("Wybicie (" + entry.getBreakoutDaysAgo() + "d)");
        }
        if (entry.isKumoTwist()) {
            signals.add("Kumo Twist");
        }
        if (entry.isChikouConfirms()) {
            signals.add("Chikou potwierdza");
        }
        if (entry.isPositiveRetest()) {
            signals.add("POZYTYWNY RETEST: " + formatPatternName(entry.getDetectedPattern())
                    + " (" + formatRetestZone(entry.getRetestZone()) + ")");
        }
    }

    private String formatPatternName(String pattern) {
        if (pattern == null) return "";
        return switch (pattern) {
            case "MORNING_STAR" -> "Gwiazda Poranna";
            case "EVENING_STAR" -> "Gwiazda Wieczorna";
            case "BULLISH_ENGULFING" -> "Objecie Hossy";
            case "BEARISH_ENGULFING" -> "Objecie Bessy";
            case "BULLISH_HARAMI" -> "Harami Wzrostowe";
            case "BEARISH_HARAMI" -> "Harami Spadkowe";
            case "PIERCING_LINE" -> "Przenikanie";
            case "DARK_CLOUD_COVER" -> "Zaslona Ciemnej Chmury";
            case "HAMMER" -> "Mlot";
            case "HANGING_MAN" -> "Wisielec";
            case "BULLISH_DOJI" -> "Doji (bycze)";
            case "BEARISH_DOJI" -> "Doji (niedzwiedzie)";
            default -> pattern;
        };
    }

    private String formatRetestZone(String zone) {
        if (zone == null) return "";
        return switch (zone) {
            case "IN_CLOUD" -> "wewnatrz chmury";
            case "CLOUD_EDGE" -> "brzeg chmury";
            default -> zone;
        };
    }

    /**
     * Detect if price was recently on the OTHER side of the cloud before the current
     * consecutive cloud period. A round-trip (other side → current side) within a
     * short window means the setup is a post-crossing artifact and should be invalidated.
     *
     * Only meaningful for entries with cloudDays < 4 months; longer setups are legitimate.
     */
    private boolean hadRecentCloudCrossing(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                                            int startIdx, TrojpolowkaEntry entry) {
        int size = candles.size();
        int breakIdx = size - 1 - entry.getCloudDays();
        if (breakIdx < startIdx || breakIdx >= size) return false;

        for (int i = breakIdx; i >= Math.max(startIdx, breakIdx - 15); i--) {
            double ct = validCloudTop(ichimoku, i);
            double cb = validCloudBottom(ichimoku, i);
            if (ct <= 0 || cb <= 0) continue;
            double close = candles.get(i).getClose();

            if ("LONG".equals(entry.getDirection()) && close > ct) return true;
            if ("SHORT".equals(entry.getDirection()) && close < cb) return true;
        }
        return false;
    }

    // ───────── HELPERS ─────────

    private double validCloudTop(List<IchimokuValues> ichimoku, int i) {
        double a = ichimoku.get(i).getSenkouSpanA();
        double b = ichimoku.get(i).getSenkouSpanB();
        if (a == 0 || b == 0) return -1;
        return Math.max(a, b);
    }

    private double validCloudBottom(List<IchimokuValues> ichimoku, int i) {
        double a = ichimoku.get(i).getSenkouSpanA();
        double b = ichimoku.get(i).getSenkouSpanB();
        if (a == 0 || b == 0) return -1;
        return Math.min(a, b);
    }

    private double calcAvgTurnover(List<StockCandle> candles, int days) {
        int size = candles.size();
        int start = Math.max(0, size - days);
        double sum = 0;
        int count = 0;
        for (int i = start; i < size; i++) {
            sum += candles.get(i).getTurnover();
            count++;
        }
        return count > 0 ? sum / count : 0;
    }

    private int findFirstFullCloudIndex(List<IchimokuValues> ichimoku) {
        for (int i = 0; i < ichimoku.size(); i++) {
            if (ichimoku.get(i).getSenkouSpanA() != 0 && ichimoku.get(i).getSenkouSpanB() != 0) {
                return i;
            }
        }
        return -1;
    }

    // ───────── LEGACY (backward compat) ─────────

    public TrojpolowkaResponse runTrojpolowkaScan() {
        List<String> allFx = new ArrayList<>();
        allFx.addAll(FOREX_MAJOR);
        allFx.addAll(FOREX_MINOR);
        allFx.addAll(FOREX_PLN);
        return runTrojpolowkaScan(true, true, allFx, 10, 500_000, 200);
    }

    public ChartDataResponse getChartData(String symbol) {
        marketDataService.resetForNewScan();
        List<StockCandle> candles = marketDataService.downloadHistory(symbol, HISTORY_DAYS);
        if (candles.size() < 100) return null;

        List<IchimokuValues> ichimoku = ichimokuCalc.calculate(candles);
        double[][] future = ichimokuCalc.getFutureCloud(candles);

        int limit = Math.min(candles.size(), 220);
        int from = candles.size() - limit;

        List<ChartDataResponse.CandlePoint> candlePoints = new ArrayList<>();
        List<ChartDataResponse.IchimokuPoint> ichiPoints = new ArrayList<>();

        for (int i = from; i < candles.size(); i++) {
            StockCandle c = candles.get(i);
            String d = c.getDate().toString();
            candlePoints.add(new ChartDataResponse.CandlePoint(d, c.getOpen(), c.getHigh(), c.getLow(), c.getClose()));

            IchimokuValues iv = ichimoku.get(i);
            Double chikou = iv.getChikouSpan();
            ichiPoints.add(new ChartDataResponse.IchimokuPoint(d,
                    iv.getTenkanSen(), iv.getKijunSen(),
                    iv.getSenkouSpanA(), iv.getSenkouSpanB(), chikou));
        }

        List<ChartDataResponse.CloudProjection> projections = new ArrayList<>();
        if (future != null) {
            java.time.LocalDate lastDate = candles.get(candles.size() - 1).getDate();
            for (int i = 0; i < future.length; i++) {
                java.time.LocalDate d = lastDate.plusDays(i + 1);
                while (d.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                    d = d.plusDays(1);
                }
                projections.add(new ChartDataResponse.CloudProjection(d.toString(), future[i][0], future[i][1]));
                lastDate = d;
            }
        }

        ChartDataResponse resp = new ChartDataResponse();
        resp.setSymbol(symbol.toUpperCase());
        resp.setCandles(candlePoints);
        resp.setIchimoku(ichiPoints);
        resp.setFutureCloud(projections);
        return resp;
    }

    public Map<String, Object> diagnoseSymbol(String symbol) {
        marketDataService.resetForNewScan();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("symbol", symbol);

        List<StockCandle> candles = marketDataService.downloadHistory(symbol, HISTORY_DAYS);
        result.put("candlesDownloaded", candles.size());
        result.put("sourceUsed", marketDataService.getLastUsedProvider());

        if (candles.size() < 100) {
            result.put("error", "Not enough candles: " + candles.size());
            return result;
        }

        int size = candles.size();
        StockCandle last = candles.get(size - 1);
        result.put("lastDate", last.getDate().toString());
        result.put("lastClose", last.getClose());

        List<IchimokuValues> ichimoku = ichimokuCalc.calculate(candles);
        IchimokuValues lastIchi = ichimoku.get(size - 1);

        double cloudTop = lastIchi.getCloudTop();
        double cloudBottom = lastIchi.getCloudBottom();
        double tenkan = lastIchi.getTenkanSen();
        double kijun = lastIchi.getKijunSen();

        result.put("senkouSpanA", lastIchi.getSenkouSpanA());
        result.put("senkouSpanB", lastIchi.getSenkouSpanB());
        result.put("cloudTop", cloudTop);
        result.put("cloudBottom", cloudBottom);
        result.put("tenkanSen", tenkan);
        result.put("kijunSen", kijun);
        double topTol = cloudTop * CLOUD_EDGE_TOLERANCE;
        double botTol = cloudBottom * CLOUD_EDGE_TOLERANCE;
        result.put("tolerance", topTol);
        result.put("closeVsCloudTop", last.getClose() - cloudTop);
        result.put("closeVsCloudTopWithTol", last.getClose() - (cloudTop + topTol));
        result.put("closeVsCloudBottom", last.getClose() - cloudBottom);

        boolean aboveCloud = last.getClose() > cloudTop + topTol;
        boolean belowCloud = last.getClose() < cloudBottom - botTol;
        result.put("cloudPosition", aboveCloud ? "ABOVE" : belowCloud ? "UNDER" : "IN");

        int startIdx = Math.max(0, size - HISTORY_DAYS);

        int longBreakoutDays = 0, longCloudDays = 0;
        if (aboveCloud) {
            longBreakoutDays = countWithGrace(candles, ichimoku, size - 1, startIdx, true, true);
            longCloudDays = countWithGrace(candles, ichimoku, size - 1 - longBreakoutDays, startIdx, true, false);
        } else {
            longCloudDays = countWithGrace(candles, ichimoku, size - 1, startIdx, true, false);
        }

        int shortBreakoutDays = 0, shortCloudDays = 0;
        if (belowCloud) {
            shortBreakoutDays = countWithGrace(candles, ichimoku, size - 1, startIdx, false, false);
            shortCloudDays = countWithGrace(candles, ichimoku, size - 1 - shortBreakoutDays, startIdx, false, true);
        } else {
            shortCloudDays = countWithGrace(candles, ichimoku, size - 1, startIdx, false, true);
        }

        result.put("longBreakoutDays", longBreakoutDays);
        result.put("longCloudDays", longCloudDays);
        result.put("shortBreakoutDays", shortBreakoutDays);
        result.put("shortCloudDays", shortCloudDays);

        boolean validLong = longCloudDays >= 20;
        boolean validShort = shortCloudDays >= 20;
        result.put("validLong", validLong);
        result.put("validShort", validShort);

        String direction = null;
        int cloudDays = 0;
        int breakoutDays = 0;
        boolean longBrk = validLong && longBreakoutDays > 0;
        boolean shortBrk = validShort && shortBreakoutDays > 0;

        if (longBrk && !shortBrk) {
            direction = "LONG"; cloudDays = longCloudDays; breakoutDays = longBreakoutDays;
        } else if (shortBrk && !longBrk) {
            direction = "SHORT"; cloudDays = shortCloudDays; breakoutDays = shortBreakoutDays;
        } else if (longBrk) {
            if (longCloudDays >= shortCloudDays) {
                direction = "LONG"; cloudDays = longCloudDays; breakoutDays = longBreakoutDays;
            } else {
                direction = "SHORT"; cloudDays = shortCloudDays; breakoutDays = shortBreakoutDays;
            }
        } else if (validLong && !validShort) {
            direction = "LONG"; cloudDays = longCloudDays;
        } else if (validShort && !validLong) {
            direction = "SHORT"; cloudDays = shortCloudDays;
        } else if (validLong) {
            if (longCloudDays >= shortCloudDays) {
                direction = "LONG"; cloudDays = longCloudDays;
            } else {
                direction = "SHORT"; cloudDays = shortCloudDays;
            }
        }

        result.put("direction", direction);
        result.put("cloudDays", cloudDays);
        result.put("breakoutDays", breakoutDays);
        result.put("has4Months", cloudDays >= FOUR_MONTHS_DAYS);

        boolean tkSwapped = false;
        if (direction != null && tenkan != 0 && kijun != 0) {
            tkSwapped = ("LONG".equals(direction) && tenkan > kijun)
                    || ("SHORT".equals(direction) && tenkan < kijun);
        }
        result.put("tkSwapped", tkSwapped);

        int column = 0;
        String col3Status = null;
        if (direction != null && cloudDays >= FOUR_MONTHS_DAYS) {
            boolean priceInCloud = !aboveCloud && !belowCloud;
            boolean breakout = breakoutDays > 0;
            if (breakout) {
                column = 3; col3Status = "IN_PLAY";
            } else if (priceInCloud) {
                column = 3; col3Status = "IN_CLOUD";
            } else if (tkSwapped) {
                column = 2;
            } else {
                column = 1;
            }
        }
        result.put("column", column);
        result.put("col3Status", col3Status);

        return result;
    }

    /**
     * Count consecutive days (backward from fromIdx) where a condition holds,
     * tolerating up to GRACE_DAYS of interruption before breaking.
     *
     * @param useCloudTop true = reference is cloudTop, false = cloudBottom
     * @param above       true = count days where close > threshold, false = close < threshold
     */
    private int countWithGrace(List<StockCandle> candles, List<IchimokuValues> ichimoku,
                               int fromIdx, int toIdx, boolean useCloudTop, boolean above) {
        int count = 0;
        int pendingGrace = 0;
        for (int i = fromIdx; i >= toIdx; i--) {
            double ref = useCloudTop ? validCloudTop(ichimoku, i) : validCloudBottom(ichimoku, i);
            if (ref <= 0) continue;
            double tol = ref * CLOUD_EDGE_TOLERANCE;
            double threshold = useCloudTop ? ref + tol : ref - tol;
            boolean qualifies = above ? candles.get(i).getClose() > threshold
                                      : candles.get(i).getClose() < threshold;
            if (qualifies) {
                count += pendingGrace + 1;
                pendingGrace = 0;
            } else {
                pendingGrace++;
                if (pendingGrace > GRACE_DAYS) break;
            }
        }
        return count;
    }
}
