import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { BankCalendarViewComponent } from './bank-calendar-view';

const API = 'http://localhost:8080/api/calendar';

describe('BankCalendarViewComponent', () => {
  let fixture: ComponentFixture<BankCalendarViewComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankCalendarViewComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BankCalendarViewComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('zeigt verfügbares Einkommen und farbige Kalendereinträge', () => {
    const month = fixture.componentInstance.monthKey();
    httpMock.expectOne(req => req.url === API && req.params.get('month') === month).flush([
      { title: 'Netflix', date: `${month}-18`, amount: -12.5, type: 'CONTRACT_PAYMENT', occurred: false },
      { title: 'Gehalt', date: `${month}-01`, amount: 2800, type: 'INCOME', occurred: true },
    ]);
    httpMock.expectOne(`${API}/available-balance`).flush({
      currentBalance: 1000,
      expectedIncome: 0,
      outstandingCharges: 12.5,
      availableBalance: 987.5,
      month,
    });
    fixture.detectChanges();

    const banner = fixture.nativeElement.querySelector('[data-testid="available-balance"]')?.textContent ?? '';
    expect(banner).toContain('987.50');
    expect(fixture.nativeElement.textContent).toContain('Netflix');
    expect(fixture.nativeElement.textContent).toContain('Gehalt');
  });
});
