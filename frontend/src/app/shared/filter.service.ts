import { Injectable, signal, computed } from '@angular/core';

const FOREX_MAJOR = ['eurusd','usdjpy','gbpusd','usdchf','audusd','usdcad','nzdusd'];
const FOREX_MINOR = [
  'cadjpy','nzdjpy','audjpy','eurjpy','cadchf','chfjpy','nzdchf',
  'eurgbp','audchf','gbpjpy','eurchf','audnzd','nzdcad','gbpchf',
  'eurcad','euraud','gbpnzd','gbpaud','eurnzd','gbpcad'
];
const FOREX_PLN = ['usdpln','eurpln','gbppln','chfpln','cadpln','audpln'];
const ALL_FOREX = [...FOREX_MAJOR, ...FOREX_MINOR, ...FOREX_PLN];

export type ChangeFilter = 'NEW' | 'MOVED_UP' | 'MOVED_DOWN' | 'UNCHANGED';

@Injectable({ providedIn: 'root' })
export class FilterService {

  readonly FOREX_MAJOR = FOREX_MAJOR;
  readonly FOREX_MINOR = FOREX_MINOR;
  readonly FOREX_PLN = FOREX_PLN;
  readonly ALL_FOREX = ALL_FOREX;

  readonly filterGpw = signal(true);
  readonly filterUs = signal(true);
  readonly selectedForex = signal<Set<string>>(new Set(ALL_FOREX));

  readonly filterLong = signal(true);
  readonly filterShort = signal(true);

  readonly turnoverDays = signal(10);
  readonly minTurnover = signal(500_000);
  readonly usTopN = signal(200);

  readonly showSettings = signal(false);

  readonly activeChangeFilters = signal<Set<ChangeFilter>>(new Set());
  readonly showDropped = signal(false);

  readonly forexState = computed(() => {
    const sel = this.selectedForex();
    if (sel.size === 0) return 'off';
    if (sel.size === ALL_FOREX.length) return 'full';
    return 'partial';
  });

  readonly majorState = computed(() => this.groupState(FOREX_MAJOR));
  readonly minorState = computed(() => this.groupState(FOREX_MINOR));
  readonly plnState = computed(() => this.groupState(FOREX_PLN));

  readonly marketDirty = computed(() =>
    !this.filterGpw() || !this.filterUs() || !this.filterLong() || !this.filterShort()
    || this.selectedForex().size !== ALL_FOREX.length
  );

  readonly changeDirty = computed(() =>
    this.activeChangeFilters().size > 0 || this.showDropped()
  );

  private groupState(group: string[]): 'all' | 'some' | 'none' {
    const sel = this.selectedForex();
    const count = group.filter(p => sel.has(p)).length;
    if (count === group.length) return 'all';
    if (count > 0) return 'some';
    return 'none';
  }

  toggleFilter(which: 'gpw' | 'us'): void {
    if (which === 'gpw') this.filterGpw.update(v => !v);
    if (which === 'us') this.filterUs.update(v => !v);
  }

  toggleForexFilter(): void {
    if (this.selectedForex().size === ALL_FOREX.length) {
      this.selectedForex.set(new Set());
    } else {
      this.selectedForex.set(new Set(ALL_FOREX));
    }
  }

  toggleGroup(group: string[]): void {
    const sel = new Set(this.selectedForex());
    const allSelected = group.every(p => sel.has(p));
    group.forEach(p => allSelected ? sel.delete(p) : sel.add(p));
    this.selectedForex.set(sel);
  }

  togglePair(pair: string): void {
    const sel = new Set(this.selectedForex());
    sel.has(pair) ? sel.delete(pair) : sel.add(pair);
    this.selectedForex.set(sel);
  }

  isPairSelected(pair: string): boolean {
    return this.selectedForex().has(pair);
  }

  toggleChangeFilter(filter: ChangeFilter): void {
    const current = new Set(this.activeChangeFilters());
    current.has(filter) ? current.delete(filter) : current.add(filter);
    this.activeChangeFilters.set(current);
  }

  isChangeFilterActive(filter: ChangeFilter): boolean {
    return this.activeChangeFilters().has(filter);
  }

  clearMarketFilters(): void {
    this.filterGpw.set(true);
    this.filterUs.set(true);
    this.filterLong.set(true);
    this.filterShort.set(true);
    this.selectedForex.set(new Set(ALL_FOREX));
  }

  clearChangeFilters(): void {
    this.activeChangeFilters.set(new Set());
    this.showDropped.set(false);
  }

  marketVisible(market: string): boolean {
    if (market === 'GPW') return this.filterGpw();
    if (market === 'US') return this.filterUs();
    if (market === 'FOREX') return this.forexState() !== 'off';
    return true;
  }

  dirVisible(dir: string): boolean {
    if (dir === 'LONG' || dir === 'BULLISH') return this.filterLong();
    if (dir === 'SHORT' || dir === 'BEARISH') return this.filterShort();
    return true;
  }

  changeVisible(status: string | null): boolean {
    const filters = this.activeChangeFilters();
    if (filters.size === 0) return true;
    if (!status) return true;
    return filters.has(status as ChangeFilter);
  }
}
