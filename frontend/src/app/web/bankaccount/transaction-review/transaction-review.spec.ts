import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { TransactionReviewComponent } from './transaction-review';
import { REVIEW_POLL_INTERVAL_MS } from '../../../service/transaction/transaction-review.store';
import { PendingReviewTransaction } from '../../../service/transaction/transaction';

const API = 'http://localhost:8080/api';

const TX: PendingReviewTransaction = {
  id: 100,
  amount: -12.5,
  purpose: 'Netflix',
  counterpartyName: 'Netflix International',
  bookingDate: '2026-09-18',
  classification: 'UNCLASSIFIED',
  suggestNewContract: true,
};

describe('TransactionReviewComponent', () => {
  let fixture: ComponentFixture<TransactionReviewComponent>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TransactionReviewComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: REVIEW_POLL_INTERVAL_MS, useValue: 0 },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TransactionReviewComponent);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    flushInitial();
  });

  afterEach(() => {
    httpMock.verify();
  });

  function query(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  function flushInitial() {
    httpMock.expectOne(`${API}/transactions/pending-review`).flush([TX]);
    httpMock.expectOne(`${API}/contracts`).flush([
      { id: 42, provider: 'Netflix', contractType: 'Streaming', monthlyCost: 12.5, endDate: '' },
    ]);
    httpMock.expectOne(`${API}/incomes`).flush([
      { id: 7, source: 'Gehalt', amount: 2800 },
    ]);
    fixture.detectChanges();
  }

  it('zeigt PENDING_REVIEW Umsätze als Karten', () => {
    expect(query('review-card-100')).toBeTruthy();
    expect(query('review-card-100')?.textContent).toContain('Netflix International');
    expect(query('review-count')?.textContent).toContain('1');
  });

  it('sendet EXISTING_MATCH mit der gewählten Vertrags-ID', async () => {
    fixture.componentInstance.onMatchChange(100, '42');
    fixture.detectChanges();

    (query('match-submit-100') as HTMLButtonElement).click();

    const request = httpMock.expectOne(`${API}/transactions/100/feedback`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      decision: 'EXISTING_MATCH',
      targetEntityId: 42,
    });
    request.flush({
      id: 100,
      classification: 'CONTRACT',
      classificationStatus: 'CLASSIFIED',
      linkedContractId: 42,
      suggestNewContract: false,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query('review-empty')).toBeTruthy();
  });

  it('öffnet das vorausgefüllte Formular und legt NEW_CONTRACT an', async () => {
    (query('new-entity-open-100') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(query('new-entity-form-100')).toBeTruthy();
    expect((query('new-entity-name') as HTMLInputElement).value).toBe('Netflix International');
    expect((query('new-entity-amount') as HTMLInputElement).value).toBe('12.5');
    expect((query('new-entity-day') as HTMLInputElement).value).toBe('18');

    (query('new-entity-submit') as HTMLButtonElement).click();

    const request = httpMock.expectOne(`${API}/transactions/100/feedback`);
    expect(request.request.body).toEqual({
      decision: 'NEW_CONTRACT',
      newContractData: {
        provider: 'Netflix International',
        contractType: undefined,
        monthlyCost: 12.5,
        dueDayOfMonth: 18,
      },
    });
    request.flush({
      id: 100,
      classification: 'CONTRACT',
      classificationStatus: 'CLASSIFIED',
      suggestNewContract: false,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query('review-card-100')).toBeNull();
  });

  it('sendet NORMAL mit optionaler Kategorie', async () => {
    fixture.componentInstance.onCategoryChange(100, 'Lebensmittel');
    fixture.detectChanges();

    (query('normal-submit-100') as HTMLButtonElement).click();

    const request = httpMock.expectOne(`${API}/transactions/100/feedback`);
    expect(request.request.body).toEqual({
      decision: 'NORMAL',
      category: 'Lebensmittel',
    });
    request.flush({
      id: 100,
      classification: 'NORMAL',
      classificationStatus: 'CLASSIFIED',
      suggestNewContract: false,
    });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(query('review-empty')).toBeTruthy();
  });
});
