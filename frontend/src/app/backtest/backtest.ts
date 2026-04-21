import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { NavBar } from '../shared/nav-bar/nav-bar';
import { API_BASE } from '../shared/api.config';

interface TradeRecord {
  symbol: string;
  market: string;
  direction: string;
  entryDate: string;
  entryPrice: number;
  initialSl: number;
  cloudTopAtEntry: number;
  cloudBottomAtEntry: number;
  exitDate: string;
  exitPrice: number;
  finalSl: number;
  exitReason: string;
  holdingDays: number;
  returnPct: number;
  rMultiple: number;
  slTrailCount: number;
  stooqUrl: string;
}

interface BacktestResult {
  totalTrades: number;
  wins: number;
  losses: number;
  winRatePct: number;
  avgReturnPct: number;
  avgWinPct: number;
  avgLossPct: number;
  maxDrawdownPct: number;
  profitFactor: number;
  avgRMultiple: number;
  avgHoldingDays: number;
  slHitRatePct: number;
  trades: TradeRecord[];
  errors: string[];
}

const FOREX_ALL = [
  'eurusd','usdjpy','gbpusd','usdchf','audusd','usdcad','nzdusd',
  'cadjpy','nzdjpy','audjpy','eurjpy','cadchf','chfjpy','nzdchf',
  'eurgbp','audchf','gbpjpy','eurchf','audnzd','nzdcad','gbpchf',
  'eurcad','euraud','gbpnzd','gbpaud','eurnzd','gbpcad',
  'usdpln','eurpln','gbppln','chfpln'
];

@Component({
  selector: 'app-backtest',
  imports: [CommonModule, FormsModule, RouterLink, NavBar],
  templateUrl: './backtest.html',
  styleUrl: './backtest.scss'
})
export class Backtest {
  data = signal<BacktestResult | null>(null);
  loading = signal(false);
  error = signal('');
  elapsed = signal(0);

  filterGpw = signal(true);
  filterUs = signal(true);
  filterForex = signal(true);
  usTopN = signal(200);
  dateFrom = signal('');
  dateTo = signal(new Date().toISOString().slice(0, 10));

  showGpw = signal(true);
  showUs = signal(true);
  showForex = signal(true);
  showLong = signal(true);
  showShort = signal(true);

  sortColumn = signal<string>('entryDate');
  sortAsc = signal(true);

  filteredTrades = computed(() => {
    const d = this.data();
    if (!d) return [];
    return d.trades.filter(t => {
      if (t.market === 'GPW' && !this.showGpw()) return false;
      if (t.market === 'US' && !this.showUs()) return false;
      if (t.market === 'FOREX' && !this.showForex()) return false;
      if (t.direction === 'LONG' && !this.showLong()) return false;
      if (t.direction === 'SHORT' && !this.showShort()) return false;
      return true;
    });
  });

  sortedTrades = computed(() => {
    const trades = [...this.filteredTrades()];
    const col = this.sortColumn();
    const asc = this.sortAsc();
    trades.sort((a: any, b: any) => {
      let va = a[col], vb = b[col];
      if (typeof va === 'string') return asc ? va.localeCompare(vb) : vb.localeCompare(va);
      return asc ? va - vb : vb - va;
    });
    return trades;
  });

  filteredStats = computed(() => {
    const trades = this.filteredTrades();
    if (trades.length === 0) return null;
    const wins = trades.filter(t => t.returnPct > 0);
    const losses = trades.filter(t => t.returnPct <= 0);
    return {
      totalTrades: trades.length,
      wins: wins.length,
      losses: losses.length,
      winRatePct: (wins.length / trades.length) * 100,
      avgReturnPct: trades.reduce((s, t) => s + t.returnPct, 0) / trades.length,
      avgRMultiple: trades.reduce((s, t) => s + t.rMultiple, 0) / trades.length,
      avgHoldingDays: trades.reduce((s, t) => s + t.holdingDays, 0) / trades.length,
      avgWinPct: wins.length > 0 ? wins.reduce((s, t) => s + t.returnPct, 0) / wins.length : 0,
      avgLossPct: losses.length > 0 ? losses.reduce((s, t) => s + t.returnPct, 0) / losses.length : 0,
      maxDrawdownPct: Math.min(...trades.map(t => t.returnPct)),
      profitFactor: losses.reduce((s, t) => s + Math.abs(t.returnPct), 0) > 0
        ? wins.reduce((s, t) => s + t.returnPct, 0) / losses.reduce((s, t) => s + Math.abs(t.returnPct), 0)
        : 0,
      slHitRatePct: (trades.filter(t => t.exitReason === 'SL_HIT').length / trades.length) * 100,
    };
  });

  constructor(private http: HttpClient) {}

  runBacktest() {
    this.loading.set(true);
    this.error.set('');
    this.data.set(null);
    this.elapsed.set(0);
    const start = Date.now();

    const fx = this.filterForex() ? FOREX_ALL.join(',') : '';
    const parts = [
      `gpw=${this.filterGpw()}`,
      `us=${this.filterUs()}`,
      `forexPairs=${fx}`,
      `usTopN=${this.usTopN()}`
    ];
    if (this.dateFrom()) parts.push(`dateFrom=${this.dateFrom()}`);
    if (this.dateTo()) parts.push(`dateTo=${this.dateTo()}`);
    const params = parts.join('&');

    this.http.get<BacktestResult>(`${API_BASE}/api/backtest/trojpolowka?${params}`).subscribe({
      next: d => { this.data.set(d); this.loading.set(false); this.elapsed.set(Math.round((Date.now() - start) / 1000)); },
      error: e => { this.error.set(e.statusText || e.message || 'Blad'); this.loading.set(false); }
    });
  }

  setSort(col: string) {
    if (this.sortColumn() === col) {
      this.sortAsc.set(!this.sortAsc());
    } else {
      this.sortColumn.set(col);
      this.sortAsc.set(col === 'entryDate');
    }
  }

  sortIcon(col: string): string {
    if (this.sortColumn() !== col) return '';
    return this.sortAsc() ? ' ▴' : ' ▾';
  }
}
