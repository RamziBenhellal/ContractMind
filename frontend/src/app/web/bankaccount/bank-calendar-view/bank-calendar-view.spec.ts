import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';

import { CALENDAR_TODAY, CalendarMonthView } from '../../../service/calendar/calendar';
import { BankCalendarViewComponent } from './bank-calendar-view';

const API = 'http://localhost:8080/api/calendar';
const ACCOUNTS_API = 'http://localhost:8080/api/bank-accounts';

describe('BankCalendarViewComponent', () => {
  let fixture: ComponentFixture<BankCalendarViewComponent>;
  let httpMock: HttpTestingController;
  let todayValue = '2026-09-19';

  const monthView: CalendarMonthView = {
    month: '2026-09',
    today: '2026-09-30',
    historicalDays: [
      {
        date: '2026-09-29',
        actualBalance: 1200,
        balancesByAccount: [{ accountId: 1, accountName: 'Giro', actualBalance: 1200 }],
        actualTransactions: [
          {
            accountId: 1,
            accountName: 'Giro',
            iban: 'DE00',
            actualBalance: 1200,
            transactions: [
              {
                id: 11,
                accountId: 1,
                accountName: 'Giro',
                amount: -800,
                purpose: 'Miete',
                counterpartyName: 'Vermieter',
              },
            ],
          },
        ],
      },
    ],
    projectedDays: [
      {
        date: '2026-09-30',
        projectedBalance: 1187.5,
        projectedEntries: [
          {
            title: 'Netflix',
            date: '2026-09-30',
            amount: -12.5,
            type: 'CONTRACT_PAYMENT',
            occurred: false,
          },
        ],
      },
    ],
  };

  const available = {
    currentBalance: 1000,
    expectedIncome: 0,
    outstandingCharges: 12.5,
    availableBalance: 987.5,
    month: '2026-09',
  };

  const accounts = [
    {
      id: 7,
      accountName: 'Giro',
      lastSyncedAt: new Date(Date.now() - 3 * 60_000).toISOString(),
    },
  ];

  beforeEach(async () => {
    todayValue = '2026-09-30';
    await TestBed.configureTestingModule({
      imports: [BankCalendarViewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: CALENDAR_TODAY, useValue: () => todayValue },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(BankCalendarViewComponent);
    fixture.componentInstance.currentYear = 2026;
    fixture.componentInstance.currentMonth = 8;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    fixture.destroy();
  });

  function flushCalendar(view: CalendarMonthView = monthView): void {
    httpMock.expectOne(req => req.url === `${API}/month-view` && req.params.get('month') === '2026-09').flush(view);
    httpMock.expectOne(`${API}/available-balance`).flush(available);
    httpMock.expectOne(ACCOUNTS_API).flush(accounts);
  }

  function render(view: CalendarMonthView = monthView): void {
    fixture.detectChanges();
    flushCalendar(view);
    fixture.detectChanges();
  }

  function clickDay(date: string): void {
    fixture.nativeElement.querySelector(`[data-testid="day-${date}"]`).click();
    fixture.detectChanges();
  }

  it('zeigt verfügbares Einkommen, reale Umsätze als IST und geplante Einträge als Prognose', () => {
    render();

    const banner = fixture.nativeElement.querySelector('[data-testid="available-balance"]')?.textContent ?? '';
    expect(banner).toContain('Aktueller Kontostand');
    expect(fixture.nativeElement.querySelector('[data-testid="current-balance"]')?.textContent).toContain('1,000.00');
    expect(fixture.nativeElement.querySelector('[data-testid="available-forecast"]')?.textContent).toContain('987.50');
    expect(fixture.nativeElement.querySelector('[data-testid="last-synced"]')?.textContent).toContain(
      'Zuletzt aktualisiert: vor 3 Min.',
    );

    const istDay = fixture.nativeElement.querySelector('[data-testid="day-2026-09-29"]');
    expect(istDay.getAttribute('data-zone')).toBe('ist');
    expect(istDay.classList.contains('zone-ist')).toBe(true);
    expect(istDay.classList.contains('last-ist')).toBe(true);
    expect(istDay.textContent).toContain('IST');
    expect(istDay.textContent).toContain('Miete');
    expect(fixture.nativeElement.querySelector('[data-testid="balance-2026-09-29"]')?.textContent).toContain('1,200.00');

    const forecastDay = fixture.nativeElement.querySelector('[data-testid="day-2026-09-30"]');
    expect(forecastDay.getAttribute('data-zone')).toBe('prognose');
    expect(forecastDay.classList.contains('zone-prognose')).toBe(true);
    expect(forecastDay.classList.contains('is-today')).toBe(true);
    expect(forecastDay.textContent).toContain('Prognose');
    expect(forecastDay.textContent).toContain('Netflix');
    expect(fixture.nativeElement.querySelector('[data-testid="balance-2026-09-30"]')?.textContent).toContain('1,000.00');
    expect(fixture.nativeElement.querySelector('[data-testid="balance-2026-09-30"]')?.textContent).not.toContain('1,187.50');
  });

  it('zeigt beim Klick auf einen Tag nur die geladenen month-view-Daten und ruft die Bank nicht an', () => {
    todayValue = '2026-09-19';
    render({
      ...monthView,
      today: '2026-09-19',
      historicalDays: [
        {
          date: '2026-09-18',
          actualBalance: 1420,
          balancesByAccount: [{ accountId: 1, accountName: 'Giro', actualBalance: 1420 }],
          actualTransactions: [
            {
              accountId: 1,
              accountName: 'Giro',
              iban: 'DE00',
              actualBalance: 1420,
              transactions: [{ id: 21, accountId: 1, accountName: 'Giro', amount: -42, purpose: 'Strom' }],
            },
          ],
        },
        {
          date: '2026-09-19',
          actualBalance: 1380,
          balancesByAccount: [{ accountId: 1, accountName: 'Giro', actualBalance: 1380 }],
          actualTransactions: [
            {
              accountId: 1,
              accountName: 'Giro',
              iban: 'DE00',
              actualBalance: 1380,
              transactions: [{ id: 22, accountId: 1, accountName: 'Giro', amount: -40, purpose: 'Supermarkt' }],
            },
          ],
        },
      ],
      projectedDays: [
        {
          date: '2026-09-25',
          projectedBalance: 900,
          projectedEntries: [
            {
              title: 'Netflix',
              date: '2026-09-25',
              amount: -12.5,
              type: 'CONTRACT_PAYMENT',
              occurred: false,
            },
          ],
        },
      ],
    });

    clickDay('2026-09-25');
    httpMock.expectNone(req => req.url === `${API}/day-view`);
    httpMock.expectNone(req => req.url.includes('/sync-now'));
    expect(fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent).toContain('Prognose');
    expect(fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent).toContain('Netflix');
    expect(fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent).toContain('Aktueller Kontostand');
    expect(fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent).toContain('1,000.00');

    clickDay('2026-09-18');
    httpMock.expectNone(req => req.url === `${API}/day-view`);
    httpMock.expectNone(req => req.url.includes('/sync-now'));
    const pastDetail = fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent ?? '';
    expect(pastDetail).toContain('Strom');
    expect(pastDetail).toContain('1,420.00');

    clickDay('2026-09-19');
    httpMock.expectNone(req => req.url === `${API}/day-view`);
    httpMock.expectNone(req => req.url.includes('/sync-now'));
    const todayDetail = fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent ?? '';
    expect(todayDetail).toContain('Supermarkt');
    expect(todayDetail).toContain('Aktueller Kontostand');
    expect(todayDetail).toContain('1,000.00');
    expect(fixture.nativeElement.querySelector('[data-testid="day-popup"]')).toBeTruthy();

    fixture.nativeElement.querySelector('.close-modal-btn').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[data-testid="day-popup"]')).toBeNull();
  });

  it('zeigt reale Umsätze auch wenn das Backend das Datum als Array liefert', () => {
    render({
      ...monthView,
      today: [2026, 9, 30] as unknown as string,
      historicalDays: [
        {
          ...monthView.historicalDays[0],
          date: [2026, 9, 29] as unknown as string,
        },
      ],
      projectedDays: [
        {
          ...monthView.projectedDays[0],
          date: [2026, 9, 30] as unknown as string,
        },
      ],
    });

    const istDay = fixture.nativeElement.querySelector('[data-testid="day-2026-09-29"]');
    expect(istDay.textContent).toContain('Miete');

    clickDay('2026-09-29');
    httpMock.expectNone(req => req.url === `${API}/day-view`);
    const detail = fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent ?? '';
    expect(detail).not.toContain('Keine realen Umsätze an diesem Tag');
    expect(detail).toContain('Miete');
    expect(detail).toContain('Giro');
  });

  it('zeichnet den Kontostand bis heute durchgezogen und danach gestrichelt', () => {
    render();

    const sparkline = fixture.nativeElement.querySelector('[data-testid="balance-sparkline"]');
    expect(sparkline).toBeTruthy();
    expect(fixture.nativeElement.querySelector('[data-testid="spark-ist"]').getAttribute('d')).toContain('M');
    expect(fixture.nativeElement.querySelector('[data-testid="spark-prognose"]').getAttribute('d')).toContain('M');
  });

  it('trennt Ist und Prognose nach Mitternacht neu, ohne die Monatssicht erneut zu laden', () => {
    render();

    const beforeMidnight = fixture.nativeElement.querySelector('[data-testid="day-2026-09-30"]');
    expect(beforeMidnight.getAttribute('data-zone')).toBe('prognose');
    expect(beforeMidnight.textContent).toContain('Netflix');
    expect(beforeMidnight.classList.contains('is-today')).toBe(true);

    todayValue = '2026-10-01';
    fixture.componentInstance.syncToday();
    fixture.detectChanges();

    const afterMidnight = fixture.nativeElement.querySelector('[data-testid="day-2026-09-30"]');
    expect(afterMidnight.getAttribute('data-zone')).toBe('ist');
    expect(afterMidnight.textContent).toContain('IST');
    expect(afterMidnight.classList.contains('is-today')).toBe(false);
    expect(afterMidnight.classList.contains('last-ist')).toBe(true);
    // Kalender-Einträge bleiben sichtbar (rot/grün), auch wenn der Tag in die IST-Zone wechselt
    expect(afterMidnight.textContent).toContain('Netflix');

    expect(fixture.nativeElement.querySelector('[data-testid="day-2026-09-29"]').getAttribute('data-zone')).toBe('ist');
    expect(fixture.nativeElement.querySelector('[data-testid="spark-prognose"]').getAttribute('d')).toBe('');
  });

  it('deaktiviert den Button bei 429 und zählt retryAfterSeconds herunter', () => {
    render();
    vi.useFakeTimers();
    try {
      fixture.nativeElement.querySelector('[data-testid="sync-now"]').click();
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="sync-spinner"]')).toBeTruthy();

      httpMock.expectOne(`${ACCOUNTS_API}/7/sync-now`).flush(
        { code: 'TOO_MANY_REQUESTS', message: 'Bitte warten', retryAfterSeconds: 3 },
        { status: 429, statusText: 'Too Many Requests' },
      );
      fixture.detectChanges();

      const button = fixture.nativeElement.querySelector('[data-testid="sync-now"]') as HTMLButtonElement;
      expect(button.disabled).toBe(true);
      expect(fixture.nativeElement.querySelector('[data-testid="sync-countdown"]')?.textContent).toContain('3s');
      expect(fixture.nativeElement.querySelector('.error-glass-box')).toBeNull();

      vi.advanceTimersByTime(1000);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="sync-countdown"]')?.textContent).toContain('2s');

      vi.advanceTimersByTime(2000);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="sync-countdown"]')).toBeNull();
      expect((fixture.nativeElement.querySelector('[data-testid="sync-now"]') as HTMLButtonElement).disabled).toBe(false);
    } finally {
      vi.useRealTimers();
    }
  });

  it('öffnet bei TAN_REQUIRED ein Modal und führt zur Reauthentifizierung', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    render();

    fixture.nativeElement.querySelector('[data-testid="sync-now"]').click();
    fixture.detectChanges();
    httpMock.expectOne(`${ACCOUNTS_API}/7/sync-now`).flush(
      { code: 'TAN_REQUIRED', message: 'TAN nötig' },
      { status: 409, statusText: 'Conflict' },
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[data-testid="reauth-modal"]')).toBeTruthy();
    expect(fixture.nativeElement.querySelector('.error-glass-box')).toBeNull();

    fixture.nativeElement.querySelector('[data-testid="reauth-wizard"]').click();
    fixture.detectChanges();
    expect(navigate).toHaveBeenCalledWith(['/bank-connect']);
    expect(fixture.nativeElement.querySelector('[data-testid="reauth-modal"]')).toBeNull();
  });

  it('lädt nach erfolgreichem Sync die Monatssicht neu und zeigt eine Bestätigung', () => {
    render();
    vi.useFakeTimers();
    try {
      fixture.nativeElement.querySelector('[data-testid="sync-now"]').click();
      fixture.detectChanges();
      httpMock.expectOne(`${ACCOUNTS_API}/7/sync-now`).flush({
        syncedAt: new Date().toISOString(),
        newTransactionCount: 3,
        updatedBalance: 524.8,
      });
      flushCalendar();
      fixture.detectChanges();

      expect(fixture.nativeElement.querySelector('[data-testid="sync-toast"]')?.textContent).toContain(
        '3 neue Transaktionen erkannt',
      );

      vi.advanceTimersByTime(4000);
      fixture.detectChanges();
      expect(fixture.nativeElement.querySelector('[data-testid="sync-toast"]')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it('zeigt Miete -650 rot am vergangenen Tag 15 und im Popup', () => {
    todayValue = '2026-09-21';
    render({
      ...monthView,
      today: '2026-09-21',
      historicalDays: [
        {
          date: '2026-09-15',
          actualBalance: 800,
          balancesByAccount: [{ accountId: 1, accountName: 'Giro', actualBalance: 800 }],
          actualTransactions: [],
          plannedEntries: [
            {
              title: 'Miete',
              date: '2026-09-15',
              amount: -650,
              type: 'CONTRACT_PAYMENT',
              occurred: true,
            },
          ],
        },
      ],
      projectedDays: [
        {
          date: '2026-09-21',
          projectedBalance: 800,
          projectedEntries: [],
        },
      ],
    });

    const day15 = fixture.nativeElement.querySelector('[data-testid="day-2026-09-15"]');
    expect(day15.getAttribute('data-zone')).toBe('ist');
    const chip = day15.querySelector('[data-testid="entry-Miete"]');
    expect(chip).toBeTruthy();
    expect(chip.getAttribute('data-tone')).toBe('charge');
    expect(chip.classList.contains('charge')).toBe(true);
    expect(chip.textContent).toContain('Miete');
    expect(chip.textContent).toContain('-650.00');

    const markers = fixture.nativeElement.querySelector('[data-testid="markers-2026-09-15"]');
    expect(markers?.getAttribute('data-charges')).toBe('1');

    clickDay('2026-09-15');
    httpMock.expectNone(req => req.url === `${API}/day-view`);
    const detail = fixture.nativeElement.querySelector('[data-testid="detail-entry-Miete"]');
    expect(detail).toBeTruthy();
    expect(detail.getAttribute('data-tone')).toBe('charge');
    expect(detail.textContent).toContain('Miete');
    expect(detail.textContent).toContain('-650.00');
    expect(detail.querySelector('strong.charge')).toBeTruthy();
  });

  it('zeigt bei Überlauf ein Mehr-Zeichen und öffnet das Popup', () => {
    todayValue = '2026-09-21';
    render({
      ...monthView,
      today: '2026-09-21',
      historicalDays: [],
      projectedDays: [
        {
          date: '2026-09-25',
          projectedBalance: 500,
          projectedEntries: [
            { title: 'Miete', date: '2026-09-25', amount: -650, type: 'CONTRACT_PAYMENT', occurred: false },
            { title: 'Netflix', date: '2026-09-25', amount: -12.5, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
      ],
    });
    fixture.componentInstance.previewLimit.set(1);
    fixture.detectChanges();

    const day = fixture.nativeElement.querySelector('[data-testid="day-2026-09-25"]');
    expect(day.querySelector('[data-testid="entry-Miete"]')).toBeTruthy();
    expect(day.querySelector('[data-testid="entry-Netflix"]')).toBeNull();
    expect(day.querySelector('[data-testid="cell-more"]')?.textContent).toContain('+1 mehr');
    expect(fixture.nativeElement.querySelector('[data-testid="balance-2026-09-25"]')?.textContent).toContain('1,000.00');

    day.querySelector('[data-testid="cell-more"]').click();
    fixture.detectChanges();
    const detail = fixture.nativeElement.querySelector('[data-testid="day-detail"]')?.textContent ?? '';
    expect(detail).toContain('Miete');
    expect(detail).toContain('Netflix');
    expect(detail).toContain('Aktueller Kontostand');
  });

  it('färbt projectedEntries nach type und zeigt die Legende', () => {
    todayValue = '2026-09-28';
    render({
      ...monthView,
      today: '2026-09-28',
      projectedDays: [
        {
          date: '2026-09-30',
          projectedBalance: 1500,
          projectedEntries: [
            { title: 'Gehalt', date: '2026-09-30', amount: 2200, type: 'INCOME', occurred: false },
            { title: 'Netflix', date: '2026-09-30', amount: -12.5, type: 'CONTRACT_PAYMENT', occurred: false },
            { title: 'Strom', date: '2026-09-30', amount: -80, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
      ],
    });

    const chargeChip = fixture.nativeElement.querySelector('[data-testid="entry-Netflix"]');
    const incomeChip = fixture.nativeElement.querySelector('[data-testid="entry-Gehalt"]');
    expect(chargeChip.getAttribute('data-tone')).toBe('charge');
    expect(chargeChip.classList.contains('charge')).toBe(true);
    expect(incomeChip.getAttribute('data-tone')).toBe('income');
    expect(incomeChip.classList.contains('income')).toBe(true);

    const legend = fixture.nativeElement.querySelector('[data-testid="entry-type-legend"]')?.textContent ?? '';
    expect(legend).toContain('Rot = Abbuchung');
    expect(legend).toContain('Grün = Einkommen');
  });

  it('zeigt Mehrfach-Marker (2 Rot, 1 Grün) am selben Tag', () => {
    todayValue = '2026-09-28';
    render({
      ...monthView,
      today: '2026-09-28',
      projectedDays: [
        {
          date: '2026-09-30',
          projectedBalance: 1500,
          projectedEntries: [
            { title: 'Gehalt', date: '2026-09-30', amount: 2200, type: 'INCOME', occurred: false },
            { title: 'Netflix', date: '2026-09-30', amount: -12.5, type: 'CONTRACT_PAYMENT', occurred: false },
            { title: 'Strom', date: '2026-09-30', amount: -80, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
      ],
    });

    const markers = fixture.nativeElement.querySelector('[data-testid="markers-2026-09-30"]');
    expect(markers).toBeTruthy();
    expect(markers.getAttribute('data-charges')).toBe('2');
    expect(markers.getAttribute('data-incomes')).toBe('1');
    expect(markers.querySelectorAll('.marker-dot.charge').length).toBe(2);
    expect(markers.querySelectorAll('.marker-dot.income').length).toBe(1);
    expect(markers.querySelector('[data-testid="marker-badge"]')?.textContent).toContain('2 Rot');
    expect(markers.querySelector('[data-testid="marker-badge"]')?.textContent).toContain('1 Grün');
  });

  it('listet die nächsten 5 Termine chronologisch mit Farbton', () => {
    todayValue = '2026-09-20';
    render({
      ...monthView,
      today: '2026-09-20',
      projectedDays: [
        {
          date: '2026-09-25',
          projectedBalance: 900,
          projectedEntries: [
            { title: 'Spotify', date: '2026-09-25', amount: -10, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
        {
          date: '2026-09-22',
          projectedBalance: 1100,
          projectedEntries: [
            { title: 'Bonus', date: '2026-09-22', amount: 100, type: 'INCOME', occurred: false },
            { title: 'Miete', date: '2026-09-22', amount: -800, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
        {
          date: '2026-09-28',
          projectedBalance: 950,
          projectedEntries: [
            { title: 'Handy', date: '2026-09-28', amount: -30, type: 'CONTRACT_PAYMENT', occurred: false },
            { title: 'Nebenjob', date: '2026-09-28', amount: 200, type: 'INCOME', occurred: false },
            { title: 'Versicherung', date: '2026-09-28', amount: -40, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
        {
          date: '2026-09-18',
          projectedBalance: 1000,
          projectedEntries: [
            { title: 'Vergangen', date: '2026-09-18', amount: -5, type: 'CONTRACT_PAYMENT', occurred: false },
          ],
        },
      ],
    });

    const panel = fixture.nativeElement.querySelector('[data-testid="upcoming-entries"]');
    expect(panel).toBeTruthy();
    const items = [...panel.querySelectorAll('.upcoming-item')];
    expect(items.length).toBe(5);
    expect(items.map((el: Element) => el.getAttribute('data-testid'))).toEqual([
      'upcoming-Bonus',
      'upcoming-Miete',
      'upcoming-Spotify',
      'upcoming-Handy',
      'upcoming-Nebenjob',
    ]);
    expect(items[0].getAttribute('data-tone')).toBe('income');
    expect(items[1].getAttribute('data-tone')).toBe('charge');
    expect(items[0].textContent).toContain('Bonus');
    expect(items[0].textContent).toContain('2026-09-22');
    expect(items[0].textContent).toContain('100.00');
    expect(panel.textContent).not.toContain('Vergangen');
    expect(panel.textContent).not.toContain('Versicherung');
  });
});
