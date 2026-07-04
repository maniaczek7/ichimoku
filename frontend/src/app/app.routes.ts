import { Routes } from '@angular/router';
import { Screener } from './screener/screener';
import { Backtest } from './backtest/backtest';
import { Fibonacci } from './fibonacci/fibonacci';
import { BacktestFib } from './backtest-fib/backtest-fib';

import { Ichimoku2 } from './ichimoku2/ichimoku2';
import { Ichimoku2Backtest } from './ichimoku2-backtest/ichimoku2-backtest';

export const routes: Routes = [
  { path: '', component: Screener },
  { path: 'ichimoku2', component: Ichimoku2 },
  { path: 'ichimoku2/backtest', component: Ichimoku2Backtest },
  { path: 'fibonacci', component: Fibonacci },
  { path: 'backtest', component: Backtest },
  { path: 'backtest-fib', component: BacktestFib },
  { path: '**', redirectTo: '' }
];
