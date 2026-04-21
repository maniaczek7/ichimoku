import { Routes } from '@angular/router';
import { Screener } from './screener/screener';
import { Backtest } from './backtest/backtest';
import { Fibonacci } from './fibonacci/fibonacci';
import { BacktestFib } from './backtest-fib/backtest-fib';

export const routes: Routes = [
  { path: '', component: Screener },
  { path: 'fibonacci', component: Fibonacci },
  { path: 'backtest', component: Backtest },
  { path: 'backtest-fib', component: BacktestFib },
  { path: '**', redirectTo: '' }
];
