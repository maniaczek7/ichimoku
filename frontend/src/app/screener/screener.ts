import { Component, signal, computed, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { NavBar } from '../shared/nav-bar/nav-bar';
import { API_BASE } from '../shared/api.config';
import { FilterService } from '../shared/filter.service';
import { createChart, CandlestickSeries, LineSeries, AreaSeries } from 'lightweight-charts';
import type { IChartApi } from 'lightweight-charts';

interface TrojpolowkaEntry {
  symbol: string;
  market: string;
  lastClose: number;
  avgTurnover10d: number;
  direction: string;
  cloudDays: number;
  cloudPosition: string;
  tkCross: boolean;
  tkCrossDirection: string;
  daysSinceTkCross: number;
  tkSwapped: boolean;
  breakoutDetected: boolean;
  breakoutDaysAgo: number;
  score: number;
  kumoTwist: boolean;
  chikouConfirms: boolean;
  chikouValue: number;
  priceAtChikou: number;
  unplayableBreakout: boolean;
  retestCount: number;
  daysSinceOpposingSignal: number;
  retestConfirmsDirection: boolean;
  secondRetestPlayable: boolean;
  positiveRetest: boolean;
  detectedPattern: string;
  retestZone: string;
  col3Status: string;
  nearCloudEdge: boolean;
  signals: string[];
  changeStatus: string | null;
  previousColumn: number;
}

interface DroppedEntry {
  symbol: string;
  market: string;
  direction: string;
  previousColumn: number;
  lastClose: number;
}

interface AnalysisLogEntry {
  symbol: string;
  market: string;
  status: string;
  detail: string;
}

interface TrojpolowkaResponse {
  column1: TrojpolowkaEntry[];
  column2: TrojpolowkaEntry[];
  column3: TrojpolowkaEntry[];
  log: AnalysisLogEntry[];
  dropped: DroppedEntry[];
  hasPreviousScan: boolean;
  previousScanTime: string | null;
  scanTime: string | null;
}

interface ChartCandlePoint { time: string; open: number; high: number; low: number; close: number; }
interface ChartIchimokuPoint { time: string; tenkan: number; kijun: number; spanA: number; spanB: number; chikou: number | null; }
interface ChartCloudProjection { time: string; spanA: number; spanB: number; }
interface ChartDataResponse {
  symbol: string;
  candles: ChartCandlePoint[];
  ichimoku: ChartIchimokuPoint[];
  futureCloud: ChartCloudProjection[];
}

type ChangeFilter = 'NEW' | 'MOVED_UP' | 'MOVED_DOWN' | 'UNCHANGED';

@Component({
  selector: 'app-screener',
  imports: [CommonModule, FormsModule, RouterLink, NavBar],
  templateUrl: './screener.html',
  styleUrl: './screener.scss'
})
export class Screener implements OnInit, OnDestroy {
  data = signal<TrojpolowkaResponse | null>(null);
  loading = signal(false);
  scanned = signal(false);
  loadedFromDb = signal(false);
  error = signal('');
  showLog = signal(false);

  readonly f: FilterService;

  filtered = computed(() => {
    const d = this.data();
    if (!d) return null;
    const filter = (list: TrojpolowkaEntry[]) => list.filter(e =>
      this.f.marketVisible(e.market) && this.f.dirVisible(e.direction) && this.f.changeVisible(e.changeStatus)
    );
    return { column1: filter(d.column1), column2: filter(d.column2), column3: filter(d.column3) };
  });

  filteredDropped = computed(() => {
    const d = this.data();
    if (!d?.dropped) return [];
    return d.dropped.filter(e =>
      this.f.marketVisible(e.market) && this.f.dirVisible(e.direction)
    );
  });

  changeStats = computed(() => {
    const d = this.data();
    if (!d) return { new_: 0, movedUp: 0, movedDown: 0, unchanged: 0, dropped: 0 };
    const all = [...d.column1, ...d.column2, ...d.column3];
    return {
      new_: all.filter(e => e.changeStatus === 'NEW').length,
      movedUp: all.filter(e => e.changeStatus === 'MOVED_UP').length,
      movedDown: all.filter(e => e.changeStatus === 'MOVED_DOWN').length,
      unchanged: all.filter(e => e.changeStatus === 'UNCHANGED').length,
      dropped: d.dropped?.length ?? 0
    };
  });

  logStats = computed(() => {
    const d = this.data();
    if (!d?.log) return { ok: 0, noSignal: 0, filtered: 0, failed: 0, total: 0 };
    const log = d.log;
    return {
      ok: log.filter(l => l.status === 'OK').length,
      noSignal: log.filter(l => l.status === 'NO_SIGNAL').length,
      filtered: log.filter(l => ['LOW_TURNOVER', 'RANKED_OUT'].includes(l.status)).length,
      failed: log.filter(l => ['DOWNLOAD_FAILED', 'INSUFFICIENT_DATA', 'ERROR'].includes(l.status)).length,
      total: log.length
    };
  });

  logGrouped = computed(() => {
    const d = this.data();
    if (!d?.log) return [];
    const groups: Record<string, AnalysisLogEntry[]> = {};
    for (const e of d.log) {
      (groups[e.status] ??= []).push(e);
    }
    const order = ['OK', 'NO_SIGNAL', 'LOW_TURNOVER', 'RANKED_OUT', 'DOWNLOAD_FAILED', 'INSUFFICIENT_DATA', 'ERROR'];
    return order.filter(s => groups[s]).map(s => ({ status: s, label: this.statusLabel(s), entries: groups[s] }));
  });

  chartSymbol = signal<string | null>(null);
  chartLoading = signal(false);
  private chartCache = new Map<string, ChartDataResponse>();
  private chartHideTimeout: any = null;
  private chartShowTimeout: any = null;
  private chartInstance: IChartApi | null = null;
  private escHandler = (e: KeyboardEvent) => { if (e.key === 'Escape') this.closeChart(); };

  constructor(private http: HttpClient, f: FilterService) {
    this.f = f;
  }

  ngOnDestroy(): void {
    this.destroyChart();
    document.removeEventListener('keydown', this.escHandler);
  }

  ngOnInit(): void {
    document.addEventListener('keydown', this.escHandler);
    this.http.get<TrojpolowkaResponse>(`${API_BASE}/api/screener/last-scan`).subscribe({
      next: (resp) => {
        if (resp && (resp.column1?.length || resp.column2?.length || resp.column3?.length)) {
          this.data.set(resp);
          this.scanned.set(true);
          this.loadedFromDb.set(true);
        }
      },
      error: () => {}
    });
  }

  // ── SCAN ──

  runScan(): void {
    this.loading.set(true);
    this.error.set('');
    this.scanned.set(false);

    const forexPairs = [...this.f.selectedForex()].join(',');
    const params = [
      `gpw=${this.f.filterGpw()}`,
      `us=${this.f.filterUs()}`,
      `forexPairs=${forexPairs}`,
      `turnoverDays=${this.f.turnoverDays()}`,
      `minTurnover=${this.f.minTurnover()}`,
      `usTopN=${this.f.usTopN()}`
    ].join('&');

    this.http.get<TrojpolowkaResponse>(`${API_BASE}/api/screener/trojpolowka?${params}`).subscribe({
      next: (resp) => {
        this.data.set(resp);
        this.loading.set(false);
        this.scanned.set(true);
        this.loadedFromDb.set(false);
      },
      error: (err) => {
        this.error.set('Blad: ' + (err.error?.message || 'sprawdz backend na porcie 8081'));
        this.loading.set(false);
      }
    });
  }

  // ── CHART POPUP ──

  onChartIconEnter(symbol: string, event: MouseEvent): void {
    event.stopPropagation();
    event.preventDefault();
    clearTimeout(this.chartHideTimeout);
    clearTimeout(this.chartShowTimeout);

    if (this.chartSymbol() === symbol) return;

    this.chartShowTimeout = setTimeout(() => {
      this.destroyChart();
      this.chartSymbol.set(symbol);

      const cached = this.chartCache.get(symbol);
      if (cached) {
        setTimeout(() => this.renderChart(cached), 20);
      } else {
        this.chartLoading.set(true);
        this.http.get<ChartDataResponse>(`${API_BASE}/api/screener/chart-data/${symbol.toLowerCase()}`).subscribe({
          next: (data) => {
            if (!data || !data.candles?.length) {
              this.chartLoading.set(false);
              return;
            }
            this.chartCache.set(symbol, data);
            this.chartLoading.set(false);
            if (this.chartSymbol() === symbol) {
              setTimeout(() => this.renderChart(data), 20);
            }
          },
          error: () => {
            this.chartLoading.set(false);
          }
        });
      }
    }, 500);
  }

  onChartPopupEnter(): void {
    clearTimeout(this.chartHideTimeout);
  }

  onChartPopupLeave(): void {
    this.chartHideTimeout = setTimeout(() => {
      this.chartSymbol.set(null);
      this.destroyChart();
    }, 500);
  }

  onChartIconLeave(): void {
    clearTimeout(this.chartShowTimeout);
    if (this.chartSymbol()) {
      this.onChartPopupLeave();
    }
  }

  closeChart(): void {
    clearTimeout(this.chartHideTimeout);
    clearTimeout(this.chartShowTimeout);
    this.chartSymbol.set(null);
    this.destroyChart();
  }

  private destroyChart(): void {
    if (this.chartInstance) {
      this.chartInstance.remove();
      this.chartInstance = null;
    }
  }

  private dedup<T extends { time: string }>(arr: T[]): T[] {
    const seen = new Set<string>();
    return arr.filter(item => {
      if (seen.has(item.time)) return false;
      seen.add(item.time);
      return true;
    });
  }

  private renderChart(data: ChartDataResponse): void {
    const container = document.getElementById('ichimoku-chart-container');
    if (!container) return;

    this.destroyChart();
    const BG = '#131722';

    const chart = createChart(container, {
      width: container.clientWidth,
      height: container.clientHeight,
      layout: { background: { color: BG }, textColor: '#787b86' },
      grid: { vertLines: { color: '#1e222d' }, horzLines: { color: '#1e222d' } },
      crosshair: { mode: 0 },
      timeScale: { borderColor: '#2b2b43', timeVisible: false, rightOffset: 44 },
      rightPriceScale: { borderColor: '#2b2b43' },
    });

    const allCloud = this.dedup([
      ...data.ichimoku.filter(i => i.spanA > 0 && i.spanB > 0).map(i => ({ time: i.time, spanA: i.spanA, spanB: i.spanB })),
      ...(data.futureCloud?.filter(f => f.spanA > 0 && f.spanB > 0).map(f => ({ time: f.time, spanA: f.spanA, spanB: f.spanB })) ?? []),
    ]);

    const spanAArea = chart.addSeries(AreaSeries, {
      topColor: 'rgba(38,166,154,0.25)', bottomColor: 'rgba(38,166,154,0.05)',
      lineColor: 'rgba(38,166,154,0.5)', lineWidth: 1,
      lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false,
    });
    spanAArea.setData(allCloud.map(c => ({ time: c.time, value: c.spanA })));

    const spanBArea = chart.addSeries(AreaSeries, {
      topColor: 'rgba(239,83,80,0.25)', bottomColor: 'rgba(239,83,80,0.05)',
      lineColor: 'rgba(239,83,80,0.5)', lineWidth: 1,
      lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false,
    });
    spanBArea.setData(allCloud.map(c => ({ time: c.time, value: c.spanB })));

    const eraserArea = chart.addSeries(AreaSeries, {
      topColor: BG, bottomColor: BG, lineColor: BG, lineWidth: 1,
      lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false,
    });
    eraserArea.setData(allCloud.map(c => ({ time: c.time, value: Math.min(c.spanA, c.spanB) })));

    const candles = this.dedup(data.candles);
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a', downColor: '#ef5350',
      borderUpColor: '#26a69a', borderDownColor: '#ef5350',
      wickUpColor: '#26a69a', wickDownColor: '#ef5350',
    });
    candleSeries.setData(candles.map(c => ({
      time: c.time, open: c.open, high: c.high, low: c.low, close: c.close
    })));

    const tenkanSeries = chart.addSeries(LineSeries, {
      color: '#e74c3c', lineWidth: 1, lastValueVisible: false, priceLineVisible: false,
    });
    const kijunSeries = chart.addSeries(LineSeries, {
      color: '#3498db', lineWidth: 1, lastValueVisible: false, priceLineVisible: false,
    });

    const validIchi = this.dedup(data.ichimoku.filter(i => i.tenkan > 0));
    tenkanSeries.setData(validIchi.map(i => ({ time: i.time, value: i.tenkan })));
    kijunSeries.setData(this.dedup(validIchi.filter(i => i.kijun > 0)).map(i => ({ time: i.time, value: i.kijun })));

    const chikouData = this.dedup(data.ichimoku.filter(i => i.chikou != null && i.chikou! > 0));
    if (chikouData.length > 0) {
      const chikouSeries = chart.addSeries(LineSeries, {
        color: '#2ecc71', lineWidth: 1, lineStyle: 2, lastValueVisible: false, priceLineVisible: false,
      });
      chikouSeries.setData(chikouData.map(i => ({ time: i.time, value: i.chikou! })));
    }

    chart.timeScale().fitContent();
    this.chartInstance = chart;
  }

  // ── HELPERS ──

  get totalCount(): number {
    const f = this.filtered();
    return f ? f.column1.length + f.column2.length + f.column3.length : 0;
  }

  formatTurnover(val: number): string {
    if (val <= 0) return '—';
    if (val >= 1_000_000) return (val / 1_000_000).toFixed(1) + 'M';
    if (val >= 1_000) return (val / 1_000).toFixed(0) + 'K';
    return val.toFixed(0);
  }

  openChart(symbol: string, event?: MouseEvent): void {
    if (event) event.stopPropagation();
    window.open('https://stooq.pl/q/a2/?s=' + symbol.toLowerCase(), '_blank');
  }

  dirLabel(e: TrojpolowkaEntry | DroppedEntry): string { return e.direction === 'LONG' ? '▲' : '▼'; }
  cloudSideLabel(e: TrojpolowkaEntry): string { return e.direction === 'LONG' ? 'pod' : 'nad'; }
  scoreArray(score: number): boolean[] { return [score >= 1, score >= 2, score >= 3]; }

  changeStatusLabel(status: string | null): string {
    if (!status) return '';
    const map: Record<string, string> = {
      NEW: 'NOWY', MOVED_UP: 'AWANS', MOVED_DOWN: 'SPADEK', UNCHANGED: 'BEZ ZMIAN'
    };
    return map[status] ?? status;
  }

  changeStatusIcon(status: string | null): string {
    if (!status) return '';
    const map: Record<string, string> = { NEW: '★', MOVED_UP: '↑', MOVED_DOWN: '↓', UNCHANGED: '' };
    return map[status] ?? '';
  }

  columnLabel(col: number): string {
    const map: Record<number, string> = { 1: 'Kol. I', 2: 'Kol. II', 3: 'Kol. III' };
    return map[col] ?? `Kol. ${col}`;
  }

  formatScanTime(): string {
    const d = this.data();
    if (!d?.scanTime) return '';
    const date = new Date(d.scanTime);
    return date.toLocaleString('pl-PL', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  formatPreviousScanTime(): string {
    const d = this.data();
    if (!d?.previousScanTime) return '';
    const date = new Date(d.previousScanTime);
    return date.toLocaleString('pl-PL', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }

  patternLabel(pattern: string): string {
    const map: Record<string, string> = {
      MORNING_STAR: 'Gwiazda Poranna', EVENING_STAR: 'Gwiazda Wieczorna',
      BULLISH_ENGULFING: 'Objecie Hossy', BEARISH_ENGULFING: 'Objecie Bessy',
      BULLISH_HARAMI: 'Harami Wzrost.', BEARISH_HARAMI: 'Harami Spadk.',
      PIERCING_LINE: 'Przenikanie', DARK_CLOUD_COVER: 'Zaslona Chmury',
      HAMMER: 'Mlot', HANGING_MAN: 'Wisielec',
      BULLISH_DOJI: 'Doji (bycze)', BEARISH_DOJI: 'Doji (niedzw.)',
    };
    return map[pattern] ?? pattern;
  }

  retestZoneLabel(zone: string): string {
    if (zone === 'IN_CLOUD') return 'w chmurze';
    if (zone === 'CLOUD_EDGE') return 'brzeg chmury';
    return zone;
  }

  statusLabel(s: string): string {
    const map: Record<string, string> = {
      OK: 'Przeanalizowano', NO_SIGNAL: 'Brak setupu', LOW_TURNOVER: 'Niski obrot',
      RANKED_OUT: 'Poza top N', DOWNLOAD_FAILED: 'Blad pobierania',
      INSUFFICIENT_DATA: 'Za malo danych', ERROR: 'Blad'
    };
    return map[s] ?? s;
  }

  statusIcon(s: string): string {
    const map: Record<string, string> = {
      OK: '✓', NO_SIGNAL: '○', LOW_TURNOVER: '↓', RANKED_OUT: '#',
      DOWNLOAD_FAILED: '✗', INSUFFICIENT_DATA: '!', ERROR: '✗'
    };
    return map[s] ?? '?';
  }
}
