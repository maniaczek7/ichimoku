import { Component, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { NavBar } from '../shared/nav-bar/nav-bar';
import { API_BASE } from '../shared/api.config';

interface Trade { symbol: string; market: string; direction: string; entryDate: string; entryPrice: number;
  initialSl: number; initialSlType: string; slMethod: string; exitDate: string; exitPrice: number;
  exitReason: string; returnPct: number; rMultiple: number; slTrailCount: number; profitViaSl: boolean; }
interface Result { totalTrades: number; wins: number; winRatePct: number; avgReturnPct: number; avgRMultiple: number;
  profitViaSlCount: number; slHitRatePct: number; slMethod: string; trades: Trade[]; errors: string[]; }

const FX = 'eurusd,usdjpy,gbpusd,usdchf,audusd,usdcad,nzdusd,cadjpy,nzdjpy,audjpy,eurjpy,cadchf,chfjpy,nzdchf,eurgbp,audchf,gbpjpy,eurchf,audnzd,nzdcad,gbpchf,eurcad,euraud,gbpnzd,gbpaud,eurnzd,gbpcad,usdpln,eurpln,gbppln,chfpln';

@Component({
  selector: 'app-ichimoku2-backtest',
  imports: [CommonModule, FormsModule, RouterLink, NavBar],
  templateUrl: './ichimoku2-backtest.html',
  styleUrl: '../backtest/backtest.scss'
})
export class Ichimoku2Backtest {
  data = signal<Result | null>(null);
  loading = signal(false);
  error = signal('');
  slMethod = signal('CONFIRMED_LOWS');
  filterGpw = signal(true); filterUs = signal(true); filterForex = signal(true);
  usTopN = signal(200);
  activeTab = signal<'ichimoku2' | 'fibo'>('ichimoku2');

  sorted = computed(() => {
    const d = this.data(); if (!d) return [];
    return [...d.trades].sort((a, b) => a.entryDate.localeCompare(b.entryDate));
  });

  constructor(private http: HttpClient) {}

  run() {
    this.loading.set(true); this.error.set(''); this.data.set(null);
    const fx = this.filterForex() ? FX : '';
    const p = `gpw=${this.filterGpw()}&us=${this.filterUs()}&forexPairs=${fx}&usTopN=${this.usTopN()}&slMethod=${this.slMethod()}`;
    this.http.get<Result>(`${API_BASE}/api/ichimoku2/backtest?${p}`).subscribe({
      next: r => { this.data.set(r); this.loading.set(false); },
      error: e => { this.error.set(e.message || 'blad'); this.loading.set(false); }
    });
  }
}
