import { Component, input, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-nav-bar',
  imports: [CommonModule, RouterLink],
  templateUrl: './nav-bar.html',
  styleUrl: './nav-bar.scss'
})
export class NavBar {
  active = input<'ichimoku' | 'fibonacci' | 'ichimoku2'>('ichimoku');
}
