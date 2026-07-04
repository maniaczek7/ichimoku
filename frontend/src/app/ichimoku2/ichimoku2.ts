import { Component, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { NavBar } from '../shared/nav-bar/nav-bar';
import { API_BASE } from '../shared/api.config';
import { FilterService } from '../shared/filter.service';

interface I2Entry {
  symbol: string; market: string; direction: string; token: string;
  cloudDays: number; score: number; lastClose: number;
  kumoTwist: boolean; chikouConfirms: boolean; fullCloudPierce: boolean;
  suggestedStopLoss: number; slType: string; signals: string[];
  changeStatus: string | null; previousColumn: number;
}
interface I2Trash { id: number; symbol: string; market: string; direction: string; token: string;
  previousColumn: number; score: number; cloudDays: number; lastClose: number;
  dropReason: string; firstSeenAt: string; lastSeenAt: string; droppedAt: string; }
interface I2Response {
  column1: I2Entry[]; column2: I2Entry[]; column3: I2Entry[];
  trashColumn1: I2Trash[]; trashColumn2: I2Trash[]; trashColumn3: I2Trash[];
  hasPreviousScan: boolean; scanTime: string | null;
}

@Component({
  selector: 'app-ichimoku2',
  imports: [CommonModule, FormsModule, RouterLink, NavBar],
  templateUrl: './ichimoku2.html',
  styleUrl: './ichimoku2.scss'
})
export class Ichimoku2 implements OnInit {
  data = signal<I2Response | null>(null);
  loading = signal(false);
  error = signal('');
  showTrash = signal(true);
  readonly f: FilterService;

  filtered = computed(() => {
    const d = this.data(); if (!d) return null;
    const fl = (l: I2Entry[]) => l.filter(e => this.f.marketVisible(e.market) && this.f.dirVisible(e.direction));
    return { c1: fl(d.column1), c2: fl(d.column2), c3: fl(d.column3),
      t1: d.trashColumn1, t2: d.trashColumn2, t3: d.trashColumn3 };
  });

  constructor(private http: HttpClient, f: FilterService) { this.f = f; }

  ngOnInit() {
    this.http.get<I2Response>(`${API_BASE}/api/ichimoku2/last-scan`).subscribe({
      next: r => { if (r?.column1?.length || r?.column2?.length || r?.column3?.length) this.data.set(r); },
      error: () => {}
    });
  }

  runScan() {
    this.loading.set(true); this.error.set('');
    const fx = [...this.f.selectedForex()].join(',');
    const p = `gpw=${this.f.filterGpw()}&us=${this.f.filterUs()}&forexPairs=${fx}&turnoverDays=${this.f.turnoverDays()}&minTurnover=${this.f.minTurnover()}&usTopN=${this.f.usTopN()}`;
    this.http.get<I2Response>(`${API_BASE}/api/ichimoku2/scan?${p}`).subscribe({
      next: r => { this.data.set(r); this.loading.set(false); },
      error: e => { this.error.set('Blad: ' + (e.message || 'backend')); this.loading.set(false); }
    });
  }

  dismissTrash(id: number, ev: Event) {
    ev.stopPropagation();
    this.http.delete(`${API_BASE}/api/ichimoku2/trash/${id}`).subscribe(() => {
      const d = this.data(); if (!d) return;
      const rm = (l: I2Trash[]) => l.filter(t => t.id !== id);
      this.data.set({ ...d, trashColumn1: rm(d.trashColumn1), trashColumn2: rm(d.trashColumn2), trashColumn3: rm(d.trashColumn3) });
    });
  }

  tokenLabel(t: string) {
    return ({ BEFORE_CLOUD: 'przed', IN_CLOUD: 'w chmurze', ABOVE_CLOUD: 'nad', BELOW_CLOUD: 'pod' } as any)[t] || t;
  }
  colLabel(n: number) { return n === 1 ? 'I' : n === 2 ? 'II' : 'III'; }
  scoreDots(s: number) { return Array.from({ length: 3 }, (_, i) => i < s); }
}
