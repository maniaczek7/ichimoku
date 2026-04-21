import { isDevMode } from '@angular/core';

export const API_BASE = isDevMode()
  ? `http://${window.location.hostname}:8081`
  : '';
