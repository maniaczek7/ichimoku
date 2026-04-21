import { Component, signal, computed, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { NavBar } from '../shared/nav-bar/nav-bar';
import { API_BASE } from '../shared/api.config';
import { FilterService } from '../shared/filter.service';
import { createChart, CandlestickSeries, LineSeries } from 'lightweight-charts';
import type { IChartApi } from 'lightweight-charts';

interface FibEntry {
  symbol: string;
  market: string;
  direction: string;
  lastClose: number;
  impulseStartDate: string;
  impulseEndDate: string;
  impulseStartPrice: number;
  impulseEndPrice: number;
  impulseHeight: number;
  impulsePct: number;
  impulseDays: number;
  fib236: number;
  fib382: number;
  fib500: number;
  fib618: number;
  fib786: number;
  retracementPct: number;
  retracementFromPeak: number;
  nearestFibLevel: string;
  distToFib50Pct: number;
  column: number;
  signals: string[];
  patterns: string[];
  fibBounce: boolean;
  inPlay: boolean;
  stopLoss: number;
  bouncePatternDate: string | null;
  changeStatus: string | null;
  previousColumn: number;
}

interface LogEntry { symbol: string; market: string; status: string; detail: string; }

interface FibDroppedEntry {
  symbol: string;
  market: string;
  direction: string;
  previousColumn: number;
  lastClose: number;
}

interface FibResponse {
  column1: FibEntry[];
  column2: FibEntry[];
  column3: FibEntry[];
  log: LogEntry[];
  dataSource: string;
  hasPreviousScan: boolean;
  previousScanTime: string | null;
  scanTime: string | null;
  dropped: FibDroppedEntry[];
}

interface FibChartData {
  symbol: string;
  candles: { time: string; open: number; high: number; low: number; close: number }[];
  direction: string;
  impulseStartDate: string;
  impulseEndDate: string;
  impulseStartPrice: number;
  impulseEndPrice: number;
  fib236: number;
  fib382: number;
  fib500: number;
  fib618: number;
  fib786: number;
  stopLoss: number;
  inPlay: boolean;
}


@Component({
  selector: 'app-fibonacci',
  imports: [CommonModule, FormsModule, RouterLink, NavBar],
  templateUrl: './fibonacci.html',
  styleUrl: './fibonacci.scss'
})
export class Fibonacci implements OnInit, OnDestroy {
  data = signal<FibResponse | null>(null);
  loading = signal(false);
  error = signal('');
  scanned = signal(false);
  loadedFromDb = signal(false);
  showLog = signal(false);

  readonly f: FilterService;

  chartSymbol = signal<string | null>(null);
  chartLoading = signal(false);
  private chartInstance: IChartApi | null = null;
  private chartCache = new Map<string, FibChartData>();
  private chartShowTimeout: any;
  private chartHideTimeout: any;

  filtered = computed(() => {
    const d = this.data();
    if (!d) return null;
    const filter = (entries: FibEntry[]) => entries.filter(e =>
      this.f.marketVisible(e.market) && this.f.dirVisible(e.direction) && this.f.changeVisible(e.changeStatus)
    );
    return { column1: filter(d.column1), column2: filter(d.column2), column3: filter(d.column3) };
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
      dropped: d.dropped?.length ?? 0,
    };
  });

  filteredDropped = computed(() => {
    const d = this.data();
    if (!d?.dropped) return [];
    return d.dropped.filter(e =>
      this.f.marketVisible(e.market) && this.f.dirVisible(e.direction)
    );
  });

  get totalCount(): number {
    const d = this.filtered();
    return d ? d.column1.length + d.column2.length + d.column3.length : 0;
  }

  logStats = computed(() => {
    const log = this.data()?.log ?? [];
    return {
      ok: log.filter(e => e.status === 'OK').length,
      noSignal: log.filter(e => e.status === 'NO_SIGNAL').length,
      failed: log.filter(e => e.status === 'ERROR').length,
      total: log.length,
    };
  });

  private escHandler = (e: KeyboardEvent) => { if (e.key === 'Escape') this.closeChart(); };

  constructor(private http: HttpClient, f: FilterService) {
    this.f = f;
  }

  ngOnInit(): void {
    document.addEventListener('keydown', this.escHandler);
    this.http.get<FibResponse>(`${API_BASE}/api/fibonacci/last-scan`).subscribe({
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

  ngOnDestroy(): void {
    this.destroyChart();
    document.removeEventListener('keydown', this.escHandler);
  }

  formatScanTime(): string {
    const t = this.data()?.scanTime;
    return t ? new Date(t).toLocaleString() : '';
  }

  formatPreviousScanTime(): string {
    const t = this.data()?.previousScanTime;
    return t ? new Date(t).toLocaleString() : '';
  }

  dirLabelDropped(e: FibDroppedEntry): string {
    return e.direction === 'BULLISH' ? '▲' : '▼';
  }

  // ── SCAN ──

  runScan() {
    this.loading.set(true);
    this.error.set('');
    this.data.set(null);

    const forexPairs = [...this.f.selectedForex()].join(',');
    const params = [
      `gpw=${this.f.filterGpw()}`,
      `us=${this.f.filterUs()}`,
      `forexPairs=${forexPairs}`,
      `turnoverDays=${this.f.turnoverDays()}`,
      `minTurnover=${this.f.minTurnover()}`,
      `usTopN=${this.f.usTopN()}`
    ].join('&');

    this.http.get<FibResponse>(`${API_BASE}/api/fibonacci/scan?${params}`).subscribe({
      next: d => {
        this.data.set(d);
        this.loading.set(false);
        this.scanned.set(true);
      },
      error: e => {
        this.error.set(e.statusText || e.message || 'Blad');
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
        this.http.get<FibChartData>(`${API_BASE}/api/fibonacci/chart-data/${symbol.toLowerCase()}`).subscribe({
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
          error: () => { this.chartLoading.set(false); }
        });
      }
    }, 500);
  }

  onChartPopupEnter(): void { clearTimeout(this.chartHideTimeout); }

  onChartPopupLeave(): void {
    this.chartHideTimeout = setTimeout(() => {
      this.chartSymbol.set(null);
      this.destroyChart();
    }, 500);
  }

  onChartIconLeave(): void {
    clearTimeout(this.chartShowTimeout);
    if (this.chartSymbol()) this.onChartPopupLeave();
  }

  closeChart(): void {
    clearTimeout(this.chartHideTimeout);
    clearTimeout(this.chartShowTimeout);
    this.chartSymbol.set(null);
    this.destroyChart();
  }

  private destroyChart(): void {
    if (this.chartInstance) { this.chartInstance.remove(); this.chartInstance = null; }
  }

  private dedup<T extends { time: string }>(arr: T[]): T[] {
    const seen = new Set<string>();
    return arr.filter(item => { if (seen.has(item.time)) return false; seen.add(item.time); return true; });
  }

  private renderChart(data: FibChartData): void {
    const container = document.getElementById('fib-chart-container');
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

    const candles = this.dedup(data.candles);
    const candleSeries = chart.addSeries(CandlestickSeries, {
      upColor: '#26a69a', downColor: '#ef5350',
      borderUpColor: '#26a69a', borderDownColor: '#ef5350',
      wickUpColor: '#26a69a', wickDownColor: '#ef5350',
    });
    candleSeries.setData(candles.map(c => ({
      time: c.time, open: c.open, high: c.high, low: c.low, close: c.close
    })));

    const fibLevels = [
      { price: data.impulseStartPrice, label: '0%', color: '#555' },
      { price: data.fib236, label: '23.6%', color: '#6366f1' },
      { price: data.fib382, label: '38.2%', color: '#3b82f6' },
      { price: data.fib500, label: '50%', color: '#f59e0b' },
      { price: data.fib618, label: '61.8%', color: '#3b82f6' },
      { price: data.fib786, label: '78.6%', color: '#6366f1' },
      { price: data.impulseEndPrice, label: '100%', color: '#555' },
    ];

    for (const fib of fibLevels) {
      const isFib50 = fib.label === '50%';
      candleSeries.createPriceLine({
        price: fib.price,
        color: fib.color,
        lineWidth: isFib50 ? 2 : 1,
        lineStyle: isFib50 ? 0 : 2,
        axisLabelVisible: true,
        title: fib.label,
      });
    }

    if (data.stopLoss > 0) {
      candleSeries.createPriceLine({
        price: data.stopLoss,
        color: '#ef4444',
        lineWidth: 2,
        lineStyle: 0,
        axisLabelVisible: true,
        title: 'SL',
      });
    }

    const impulseColor = data.direction === 'BULLISH' ? '#22c55e' : '#ef4444';
    const impulseLine = chart.addSeries(LineSeries, {
      color: impulseColor, lineWidth: 2, lineStyle: 2,
      lastValueVisible: false, priceLineVisible: false, crosshairMarkerVisible: false,
    });
    impulseLine.setData([
      { time: data.impulseStartDate, value: data.impulseStartPrice },
      { time: data.impulseEndDate, value: data.impulseEndPrice },
    ]);

    chart.timeScale().fitContent();
    this.chartInstance = chart;
  }

  // ── HELPERS ──

  dirLabel(e: FibEntry): string {
    return e.direction === 'BULLISH' ? '▲' : '▼';
  }

  openChart(symbol: string) {
    window.open('https://stooq.pl/q/a2/?s=' + symbol.toLowerCase(), '_blank');
  }

  statusIcon(status: string): string {
    switch (status) {
      case 'OK': return '✓';
      case 'NO_SIGNAL': return '○';
      case 'ERROR': return '✗';
      default: return '?';
    }
  }

  hasAlert(e: FibEntry): boolean {
    return e.fibBounce && e.inPlay;
  }

  changeStatusIcon(status: string | null): string {
    switch (status) {
      case 'NEW': return '★';
      case 'MOVED_UP': return '↑';
      case 'MOVED_DOWN': return '↓';
      default: return '';
    }
  }

  changeStatusLabel(status: string | null): string {
    switch (status) {
      case 'NEW': return 'NOWY';
      case 'MOVED_UP': return 'AWANS';
      case 'MOVED_DOWN': return 'SPADEK';
      default: return '';
    }
  }

  columnLabel(col: number): string {
    switch (col) {
      case 1: return 'I (Impuls)';
      case 2: return 'II (Zawraca)';
      case 3: return 'III (Fib 50%)';
      default: return '';
    }
  }
}
