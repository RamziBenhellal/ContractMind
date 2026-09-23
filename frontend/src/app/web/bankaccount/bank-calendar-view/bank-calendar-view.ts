import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import {
  AccountBalance,
  AccountTransactionGroup,
  ActualTransaction,
  AvailableBalance,
  CALENDAR_TODAY,
  CalendarMonthView,
  CalendarOccurrence,
  CalendarService,
  HistoricalDay,
  ProjectedDay,
  toDayKey,
} from '../../../service/calendar/calendar';
import { BankAccount, BankAccountService, SyncNowResponse } from '../../../service/bankaccount/bankaccount';

export type CalendarZone = 'ist' | 'prognose';
export type EntryMarkerTone = 'charge' | 'income';

export interface DayEntryMarkers {
  charges: number;
  incomes: number;
}

export interface CalendarDay {
  date: Date;
  dayOfMonth: number;
  isCurrentMonth: boolean;
  dateString: string;
  zone: CalendarZone;
  isToday: boolean;
  isLastIst: boolean;
  /** Historischer Tagesstand (nur Vergangenheit). Heute/Zukunft: siehe cellBalance(). */
  balance: number | null;
  actualGroups: AccountTransactionGroup[];
  balancesByAccount: AccountBalance[];
  projectedEntries: CalendarOccurrence[];
}

export interface CellPreviewEntry {
  key: string;
  title: string;
  amount: number;
  tone: EntryMarkerTone | null;
  dashed: boolean;
}

export interface SparklinePoint {
  x: number;
  y: number;
  date: string;
  zone: CalendarZone;
}

/** Desktop-Vorschau; mobil/tablet reduziert die Komponente den Wert dynamisch. */
export const CELL_PREVIEW_LIMIT = 3;
export const CELL_PREVIEW_LIMIT_TABLET = 2;
export const CELL_PREVIEW_LIMIT_MOBILE = 1;

/** Rein visuell: type → Farbton. Beeinflusst keine Balance-Berechnung. */
export function toneForEntryType(type: string | undefined | null): EntryMarkerTone | null {
  if (type === 'CONTRACT_PAYMENT') return 'charge';
  if (type === 'INCOME') return 'income';
  return null;
}

export function markersFromEntries(entries: CalendarOccurrence[] | null | undefined): DayEntryMarkers {
  let charges = 0;
  let incomes = 0;
  for (const entry of entries ?? []) {
    const tone = toneForEntryType(entry.type);
    if (tone === 'charge') charges++;
    else if (tone === 'income') incomes++;
  }
  return { charges, incomes };
}

/** Nächste anstehende Einträge aus projectedDays, chronologisch, max. limit. */
export function collectUpcomingEntries(
  view: CalendarMonthView | null,
  today: string,
  limit = 5,
): CalendarOccurrence[] {
  const items: CalendarOccurrence[] = [];
  for (const day of view?.projectedDays ?? []) {
    const dayKey = toDayKey(day.date);
    if (!dayKey || dayKey < today) continue;
    for (const entry of day.projectedEntries ?? []) {
      items.push({
        ...entry,
        date: toDayKey(entry.date) || dayKey,
      });
    }
  }
  items.sort((a, b) => a.date.localeCompare(b.date) || (a.title || '').localeCompare(b.title || ''));
  return items.slice(0, limit);
}

@Component({
  selector: 'app-bank-calendar-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './bank-calendar-view.html',
  styleUrls: ['./bank-calendar-view.css'],
})
export class BankCalendarViewComponent implements OnInit, OnDestroy {
  private readonly calendarService = inject(CalendarService);
  private readonly bankAccountService = inject(BankAccountService);
  private readonly router = inject(Router);
  private readonly todayFn = inject(CALENDAR_TODAY);
  private clockTimer: ReturnType<typeof setInterval> | undefined;
  private countdownTimer: ReturnType<typeof setInterval> | undefined;
  private toastTimer: ReturnType<typeof setTimeout> | undefined;

  readonly monthNames = [
    'Januar',
    'Februar',
    'März',
    'April',
    'Mai',
    'Juni',
    'Juli',
    'August',
    'September',
    'Oktober',
    'November',
    'Dezember',
  ];
  readonly weekDays = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];

  currentYear = new Date().getFullYear();
  currentMonth = new Date().getMonth();
  calendarGrid: CalendarDay[] = [];
  readonly available = signal<AvailableBalance | null>(null);
  readonly monthView = signal<CalendarMonthView | null>(null);
  readonly error = signal<string | null>(null);
  readonly selectedDay = signal<CalendarDay | null>(null);
  readonly now = signal(this.todayFn());
  readonly syncing = signal(false);
  readonly retryAfterSeconds = signal(0);
  readonly lastSyncedAt = signal<string | null>(null);
  readonly clockMs = signal(Date.now());
  readonly toast = signal<string | null>(null);
  readonly reauthOpen = signal(false);
  readonly primaryAccountId = signal<number | null>(null);
  /** Responsive: wie viele Einträge die Zelle zeigt, bevor „mehr“ erscheint. */
  readonly previewLimit = signal(CELL_PREVIEW_LIMIT);

  readonly lastSyncedLabel = computed(() => formatLastSynced(this.lastSyncedAt(), this.clockMs()));
  readonly sparkline = computed(() =>
    this.buildSparkline(this.monthView(), this.now(), this.available()?.currentBalance ?? null),
  );
  readonly upcomingEntries = computed(() => collectUpcomingEntries(this.monthView(), this.now(), 5));
  readonly syncDisabled = computed(
    () => this.syncing() || this.retryAfterSeconds() > 0 || this.primaryAccountId() == null,
  );

  private mediaMobile?: MediaQueryList;
  private mediaTablet?: MediaQueryList;
  private readonly onViewportChange = () => this.syncPreviewLimit();

  entryTone(type: string | undefined | null): EntryMarkerTone | null {
    return toneForEntryType(type);
  }

  markersFor(day: CalendarDay): DayEntryMarkers {
    return markersFromEntries(day.projectedEntries);
  }

  /** Ein Dot pro projectedEntry — Reihenfolge: Abbuchungen, dann Einkommen. */
  markerDots(day: CalendarDay): EntryMarkerTone[] {
    const markers = this.markersFor(day);
    return [
      ...Array.from({ length: markers.charges }, (): EntryMarkerTone => 'charge'),
      ...Array.from({ length: markers.incomes }, (): EntryMarkerTone => 'income'),
    ];
  }

  markerSummary(day: CalendarDay): string {
    const markers = this.markersFor(day);
    const parts: string[] = [];
    if (markers.charges > 0) parts.push(`${markers.charges} Rot`);
    if (markers.incomes > 0) parts.push(`${markers.incomes} Grün`);
    return parts.join(', ');
  }

  /** Alle Einträge einer Zelle (Ist-Umsätze + geplante Abbuchungen/Einkommen). */
  cellEntries(day: CalendarDay): CellPreviewEntry[] {
    const items: CellPreviewEntry[] = [];
    if (this.zoneFor(day.dateString) === 'ist') {
      for (const group of day.actualGroups) {
        for (const tx of group.transactions) {
          const amount = Number(tx.amount);
          items.push({
            key: `tx-${tx.id}`,
            title: tx.purpose || tx.counterpartyName || group.accountName || 'Umsatz',
            amount,
            tone: amount > 0 ? 'income' : amount < 0 ? 'charge' : null,
            dashed: false,
          });
        }
      }
    }
    for (const entry of day.projectedEntries) {
      items.push({
        key: `entry-${entry.sourceEntryId ?? entry.title}-${entry.date}-${entry.type}`,
        title: entry.title || 'Eintrag',
        amount: Number(entry.amount),
        tone: this.entryTone(entry.type),
        dashed: this.zoneFor(day.dateString) === 'prognose',
      });
    }
    return items;
  }

  visibleCellEntries(day: CalendarDay): CellPreviewEntry[] {
    return this.cellEntries(day).slice(0, this.previewLimit());
  }

  overflowEntryCount(day: CalendarDay): number {
    return Math.max(0, this.cellEntries(day).length - this.previewLimit());
  }

  hasCellOverflow(day: CalendarDay): boolean {
    return this.overflowEntryCount(day) > 0;
  }

  /**
   * Kontostand in der Zelle: Vergangenheit = realer Tagesstand,
   * heute/Zukunft = aktueller Kontostand (ohne geplante Verträge/Einkommen).
   */
  cellBalance(day: CalendarDay): number | null {
    if (!day.isCurrentMonth) {
      return null;
    }
    if (day.dateString < this.today()) {
      return day.balance;
    }
    const current = this.available()?.currentBalance;
    return current != null ? Number(current) : null;
  }

  ngOnInit(): void {
    this.syncToday();
    this.bindViewportQueries();
    this.reload();
    this.clockTimer = setInterval(() => {
      this.syncToday();
      this.clockMs.set(Date.now());
    }, 60_000);
  }

  ngOnDestroy(): void {
    if (this.clockTimer) {
      clearInterval(this.clockTimer);
    }
    this.stopCountdown();
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.unbindViewportQueries();
  }

  private bindViewportQueries(): void {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
      return;
    }
    this.mediaMobile = window.matchMedia('(max-width: 720px)');
    this.mediaTablet = window.matchMedia('(max-width: 1100px)');
    this.mediaMobile.addEventListener('change', this.onViewportChange);
    this.mediaTablet.addEventListener('change', this.onViewportChange);
    this.syncPreviewLimit();
  }

  private unbindViewportQueries(): void {
    this.mediaMobile?.removeEventListener('change', this.onViewportChange);
    this.mediaTablet?.removeEventListener('change', this.onViewportChange);
  }

  private syncPreviewLimit(): void {
    if (this.mediaMobile?.matches) {
      this.previewLimit.set(CELL_PREVIEW_LIMIT_MOBILE);
      return;
    }
    if (this.mediaTablet?.matches) {
      this.previewLimit.set(CELL_PREVIEW_LIMIT_TABLET);
      return;
    }
    this.previewLimit.set(CELL_PREVIEW_LIMIT);
  }

  today(): string {
    return this.now();
  }

  /** Liest die lokale Uhr neu, damit Ist/Prognose nach Mitternacht ohne Reload wechseln. */
  syncToday(): void {
    this.now.set(this.todayFn());
    this.markLastIst();
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
    this.closeDayPopup();
    this.reload();
  }

  monthKey(): string {
    return `${this.currentYear}-${String(this.currentMonth + 1).padStart(2, '0')}`;
  }

  zoneFor(dateString: string): CalendarZone {
    return dateString < this.now() ? 'ist' : 'prognose';
  }

  selectDay(day: CalendarDay): void {
    if (!day.isCurrentMonth) return;
    this.selectedDay.set(day);
  }

  closeDayPopup(): void {
    this.selectedDay.set(null);
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.reauthOpen()) {
      this.closeReauth();
      return;
    }
    this.closeDayPopup();
  }

  transactionsFor(accountId: number, day: CalendarDay): ActualTransaction[] {
    return day.actualGroups.find(group => group.accountId === accountId)?.transactions ?? [];
  }

  refreshNow(): void {
    if (this.syncDisabled()) {
      return;
    }
    const accountId = this.primaryAccountId();
    if (accountId == null) {
      return;
    }
    this.syncing.set(true);
    this.error.set(null);
    this.bankAccountService.syncNow(accountId).subscribe({
      next: result => this.onSyncSuccess(result),
      error: err => this.onSyncError(err),
    });
  }

  goToReauth(): void {
    this.closeReauth();
    void this.router.navigate(['/bank-connect']);
  }

  closeReauth(): void {
    this.reauthOpen.set(false);
  }

  reload(): void {
    this.error.set(null);
    this.generateGrid();
    this.calendarService.getMonthView(this.monthKey()).subscribe({
      next: view => {
        this.monthView.set(view);
        this.attachMonthView(view);
      },
      error: () => this.error.set('Kalenderdaten konnten nicht geladen werden.'),
    });
    this.calendarService.getAvailableBalance().subscribe({
      next: balance => this.available.set(balance),
      error: () => this.error.set('Verfügbares Einkommen konnte nicht geladen werden.'),
    });
    this.bankAccountService.getBankAccounts().subscribe({
      next: accounts => this.applyAccounts(accounts),
      error: () => undefined,
    });
  }

  private onSyncSuccess(result: SyncNowResponse): void {
    this.syncing.set(false);
    if (result.syncedAt) {
      this.lastSyncedAt.set(result.syncedAt);
      this.clockMs.set(Date.now());
    }
    this.showToast(syncToastMessage(result.newTransactionCount ?? 0));
    this.reload();
  }

  private onSyncError(err: unknown): void {
    this.syncing.set(false);
    const http = err as HttpErrorResponse;
    // TEMP debug: echte Sync-Fehlerdetails nicht verwerfen
    console.error('[sync-now] failed', {
      status: http?.status,
      statusText: http?.statusText,
      url: http?.url,
      body: http?.error,
      message: http?.message,
      name: http?.name,
      error: err,
    });
    const body = (typeof http?.error === 'object' && http.error != null ? http.error : {}) as {
      code?: string;
      message?: string;
      retryAfterSeconds?: number;
    };
    const code = body.code;
    const readable =
      body.message ||
      (typeof http?.error === 'string' && http.error.trim() ? http.error : null);

    if (http?.status === 429 || code === 'TOO_MANY_REQUESTS') {
      const headerRetry = Number(http.headers?.get('Retry-After'));
      const seconds = Number(body.retryAfterSeconds ?? (Number.isFinite(headerRetry) ? headerRetry : 300));
      this.startCountdown(Math.max(1, Math.floor(seconds)));
      return;
    }
    if (code === 'TAN_REQUIRED' || code === 'CONNECTION_EXPIRED') {
      this.reauthOpen.set(true);
      return;
    }
    if (http?.status === 401 || http?.status === 403 || code === 'UNAUTHORIZED' || code === 'FORBIDDEN') {
      this.error.set(readable || 'Sitzung abgelaufen oder ungültig. Bitte erneut einloggen.');
      return;
    }
    if (code === 'TIMEOUT' || code === 'DECRYPTION_ERROR' || code === 'BANK_UNAVAILABLE' || code === 'BANK_UNREACHABLE') {
      this.error.set(readable || code);
      return;
    }
    this.error.set(
      readable ||
        (code ? `${code}` : null) ||
        (http?.status ? `Aktualisierung fehlgeschlagen (HTTP ${http.status}).` : 'Aktualisierung fehlgeschlagen.'),
    );
  }

  private startCountdown(seconds: number): void {
    this.retryAfterSeconds.set(seconds);
    this.stopCountdown();
    this.countdownTimer = setInterval(() => {
      const left = this.retryAfterSeconds();
      if (left <= 1) {
        this.retryAfterSeconds.set(0);
        this.stopCountdown();
        return;
      }
      this.retryAfterSeconds.set(left - 1);
    }, 1000);
  }

  private stopCountdown(): void {
    if (this.countdownTimer) {
      clearInterval(this.countdownTimer);
      this.countdownTimer = undefined;
    }
  }

  private showToast(message: string): void {
    this.toast.set(message);
    if (this.toastTimer) {
      clearTimeout(this.toastTimer);
    }
    this.toastTimer = setTimeout(() => this.toast.set(null), 4000);
  }

  private applyAccounts(accounts: BankAccount[]): void {
    const withId = accounts.filter(account => account.id != null);
    this.primaryAccountId.set(withId[0]?.id ?? null);
    const latest = withId
      .map(account => account.lastSyncedAt)
      .filter((value): value is string => !!value)
      .sort()
      .at(-1);
    this.lastSyncedAt.set(latest ?? null);
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
    this.markLastIst();
  }

  private attachMonthView(view: CalendarMonthView): void {
    const historical = new Map<string, HistoricalDay>();
    for (const day of view.historicalDays ?? []) {
      historical.set(toDayKey(day.date), day);
    }
    const projected = new Map<string, ProjectedDay>();
    for (const day of view.projectedDays ?? []) {
      projected.set(toDayKey(day.date), day);
    }
    this.calendarGrid = this.calendarGrid.map(day => {
      const hist = historical.get(day.dateString);
      const proj = projected.get(day.dateString);
      return {
        ...day,
        zone: this.zoneFor(day.dateString),
        isToday: day.dateString === this.today(),
        balance: hist?.actualBalance ?? null,
        actualGroups: hist?.actualTransactions ?? [],
        balancesByAccount: hist?.balancesByAccount ?? [],
        projectedEntries: hist?.plannedEntries?.length
          ? hist.plannedEntries
          : (proj?.projectedEntries ?? []),
      };
    });
    this.markLastIst();
    this.refreshSelectedDay();
  }

  private refreshSelectedDay(): void {
    const selected = this.selectedDay();
    if (!selected) {
      return;
    }
    const updated = this.calendarGrid.find(day => day.dateString === selected.dateString);
    if (updated) {
      this.selectedDay.set(updated);
    }
  }

  private markLastIst(): void {
    let lastIst: CalendarDay | undefined;
    for (const day of this.calendarGrid) {
      day.isLastIst = false;
      day.zone = this.zoneFor(day.dateString);
      day.isToday = day.dateString === this.today();
      if (day.isCurrentMonth && day.zone === 'ist') {
        lastIst = day;
      }
    }
    if (lastIst) {
      lastIst.isLastIst = true;
    }
  }

  private createDay(date: Date, isCurrentMonth: boolean): CalendarDay {
    const dateString = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    return {
      date,
      dayOfMonth: date.getDate(),
      isCurrentMonth,
      dateString,
      zone: this.zoneFor(dateString),
      isToday: dateString === this.today(),
      isLastIst: false,
      balance: null,
      actualGroups: [],
      balancesByAccount: [],
      projectedEntries: [],
    };
  }

  private buildSparkline(
    view: CalendarMonthView | null,
    today: string,
    currentBalance: number | null,
  ): {
    actual: string;
    forecast: string;
    points: SparklinePoint[];
  } {
    const series: { date: string; balance: number; zone: CalendarZone }[] = [];
    for (const day of view?.historicalDays ?? []) {
      series.push({ date: day.date, balance: Number(day.actualBalance), zone: 'ist' });
    }
    // Prognose-Linie = aktueller Kontostand (ohne geplante Verträge/Einkommen).
    const forecastBalance = currentBalance != null ? Number(currentBalance) : null;
    for (const day of view?.projectedDays ?? []) {
      const balance = forecastBalance != null ? forecastBalance : Number(day.projectedBalance);
      series.push({ date: day.date, balance, zone: 'prognose' });
    }
    series.sort((a, b) => a.date.localeCompare(b.date));
    if (series.length === 0) {
      return { actual: '', forecast: '', points: [] };
    }

    const width = 320;
    const height = 56;
    const pad = 4;
    const values = series.map(item => item.balance);
    const min = Math.min(...values);
    const max = Math.max(...values);
    const span = max - min || 1;
    const points: SparklinePoint[] = series.map((item, index) => ({
      x: pad + (index * (width - pad * 2)) / Math.max(series.length - 1, 1),
      y: height - pad - ((item.balance - min) / span) * (height - pad * 2),
      date: item.date,
      zone: item.date < today ? 'ist' : 'prognose',
    }));

    const actualPoints = points.filter(point => point.date <= today);
    const forecastPoints = points.filter(point => point.date >= today);
    return {
      actual: toPath(actualPoints),
      forecast: toPath(forecastPoints),
      points,
    };
  }
}

function toPath(points: SparklinePoint[]): string {
  if (points.length === 0) return '';
  return points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' ');
}

function formatLastSynced(syncedAt: string | null, nowMs: number): string {
  if (!syncedAt) {
    return 'Noch nicht aktualisiert';
  }
  const then = Date.parse(syncedAt);
  if (!Number.isFinite(then)) {
    return 'Noch nicht aktualisiert';
  }
  const mins = Math.max(0, Math.floor((nowMs - then) / 60_000));
  if (mins < 1) {
    return 'Zuletzt aktualisiert: gerade eben';
  }
  if (mins === 1) {
    return 'Zuletzt aktualisiert: vor 1 Min.';
  }
  if (mins < 60) {
    return `Zuletzt aktualisiert: vor ${mins} Min.`;
  }
  const hours = Math.floor(mins / 60);
  if (hours === 1) {
    return 'Zuletzt aktualisiert: vor 1 Std.';
  }
  if (hours < 24) {
    return `Zuletzt aktualisiert: vor ${hours} Std.`;
  }
  const days = Math.floor(hours / 24);
  return days === 1 ? 'Zuletzt aktualisiert: vor 1 Tag' : `Zuletzt aktualisiert: vor ${days} Tagen`;
}

function syncToastMessage(count: number): string {
  if (count === 1) {
    return '1 neue Transaktion erkannt';
  }
  return `${count} neue Transaktionen erkannt`;
}
