import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { AvailableBalance, CalendarOccurrence, CalendarService } from '../../../service/calendar/calendar';

interface CalendarDay {
  date: Date;
  dayOfMonth: number;
  isCurrentMonth: boolean;
  dateString: string;
  entries: CalendarOccurrence[];
}

@Component({
  selector: 'app-bank-calendar-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bank-calendar-view.html',
  styleUrls: ['./bank-calendar-view.css'],
})
export class BankCalendarViewComponent implements OnInit {
  private readonly calendarService = inject(CalendarService);

  readonly monthNames = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
  readonly weekDays = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

  currentYear = new Date().getFullYear();
  currentMonth = new Date().getMonth();
  calendarGrid: CalendarDay[] = [];
  entries: CalendarOccurrence[] = [];
  readonly available = signal<AvailableBalance | null>(null);
  readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.reload();
  }

  changeMonth(delta: number): void {
    this.currentMonth += delta;
    if (this.currentMonth > 11) {
      this.currentMonth = 0;
      this.currentYear++;
    } else if (this.currentMonth < 0) {
      this.currentMonth = 11;
      this.currentYear--;
    }
    this.reload();
  }

  monthKey(): string {
    return `${this.currentYear}-${String(this.currentMonth + 1).padStart(2, '0')}`;
  }

  private reload(): void {
    this.generateGrid();
    this.calendarService.getMonth(this.monthKey()).subscribe({
      next: entries => {
        this.entries = entries ?? [];
        this.attachEntries();
      },
      error: () => this.error.set('Kalenderdaten konnten nicht geladen werden.'),
    });
    this.calendarService.getAvailableBalance().subscribe({
      next: balance => this.available.set(balance),
      error: () => this.error.set('Verfügbares Einkommen konnte nicht geladen werden.'),
    });
  }

  private generateGrid(): void {
    this.calendarGrid = [];
    const firstDayOfMonth = new Date(this.currentYear, this.currentMonth, 1);
    const lastDayOfMonth = new Date(this.currentYear, this.currentMonth + 1, 0);
    let startDayOfWeek = firstDayOfMonth.getDay();
    if (startDayOfWeek === 0) {
      startDayOfWeek = 7;
    }

    for (let i = startDayOfWeek - 1; i > 0; i--) {
      this.calendarGrid.push(this.createDay(new Date(this.currentYear, this.currentMonth, 1 - i), false));
    }
    for (let i = 1; i <= lastDayOfMonth.getDate(); i++) {
      this.calendarGrid.push(this.createDay(new Date(this.currentYear, this.currentMonth, i), true));
    }
    const remaining = 42 - this.calendarGrid.length;
    for (let i = 1; i <= remaining; i++) {
      this.calendarGrid.push(this.createDay(new Date(this.currentYear, this.currentMonth + 1, i), false));
    }
  }

  private attachEntries(): void {
    const byDate = new Map<string, CalendarOccurrence[]>();
    for (const entry of this.entries) {
      const list = byDate.get(entry.date) ?? [];
      list.push(entry);
      byDate.set(entry.date, list);
    }
    this.calendarGrid = this.calendarGrid.map(day => ({
      ...day,
      entries: byDate.get(day.dateString) ?? [],
    }));
  }

  private createDay(date: Date, isCurrentMonth: boolean): CalendarDay {
    const dateString = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().split('T')[0];
    return {
      date,
      dayOfMonth: date.getDate(),
      isCurrentMonth,
      dateString,
      entries: [],
    };
  }
}
