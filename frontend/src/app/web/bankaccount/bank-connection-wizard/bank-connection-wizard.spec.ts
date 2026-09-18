import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import {
  BANK_SEARCH_DEBOUNCE_MS,
  BankConnectionWizardComponent,
  maskIban,
} from './bank-connection-wizard';
import { Bank, ConnectedAccount, TanMethod } from '../../../service/bankconnection/bankconnection';

const API = 'http://localhost:8080/api';

const SPARKASSE: Bank = { blz: '50050201', name: 'Frankfurter Sparkasse' };

const CHIPTAN: TanMethod = { id: 'chipTAN', name: 'chipTAN' };
const PUSHTAN: TanMethod = { id: 'pushTAN', name: 'pushTAN', hint: 'Schau in deine Banking-App.' };

const ACCOUNTS: ConnectedAccount[] = [
  { iban: 'DE89370400440532013000', accountType: 'Girokonto', balance: 1420.5 },
  { iban: 'DE12500105170648489890', accountType: 'Tagesgeld', balance: -35 },
];

describe('BankConnectionWizardComponent', () => {
  let fixture: ComponentFixture<BankConnectionWizardComponent>;
  let component: BankConnectionWizardComponent;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankConnectionWizardComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BankConnectionWizardComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
  });

  // ------------------------------------------------------------------
  // Hilfsfunktionen, die den Wizard bis zu einem bestimmten Schritt fahren
  // ------------------------------------------------------------------

  function query(testId: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testId}"]`);
  }

  /** Wartet die Debounce-Zeit der Autocomplete ab */
  async function typeQuery(value: string) {
    component.onQueryChange(value);
    await new Promise(resolve => setTimeout(resolve, BANK_SEARCH_DEBOUNCE_MS + 50));
  }

  function goToCredentials() {
    component.selectBank(SPARKASSE);
    fixture.detectChanges();
  }

  function submitCredentials(tanMethods: TanMethod[]) {
    component.loginId.set('1234567');
    component.pin.set('geheim');
    component.submitCredentials();

    httpMock
      .expectOne(`${API}/bank-connections`)
      .flush({ connectionId: 'conn-1', tanMethods });
    fixture.detectChanges();
  }

  function goToTanEntry() {
    goToCredentials();
    submitCredentials([CHIPTAN]);
  }

  // ==================================================================
  // SCHRITT 1: BANK SUCHEN
  // ==================================================================

  describe('Schritt 1: Bank suchen', () => {
    it('startet auf der Banksuche', () => {
      expect(component.step()).toBe('bank-search');
      expect(query('step-bank-search')).toBeTruthy();
    });

    it('sucht nach Bankname und zeigt die Treffer an', async () => {
      await typeQuery('Sparkasse');

      const request = httpMock.expectOne(req => req.url === `${API}/banks/search`);
      expect(request.request.method).toBe('GET');
      expect(request.request.params.get('query')).toBe('Sparkasse');

      request.flush([SPARKASSE]);
      fixture.detectChanges();

      expect(component.bankResults()).toEqual([SPARKASSE]);
      expect(query('bank-results')?.textContent).toContain('Frankfurter Sparkasse');
      expect(query('bank-results')?.textContent).toContain('50050201');
    });

    it('sucht auch nach der BLZ', async () => {
      await typeQuery('50050201');

      const request = httpMock.expectOne(req => req.url === `${API}/banks/search`);
      expect(request.request.params.get('query')).toBe('50050201');
      request.flush([SPARKASSE]);
    });

    it('fragt bei weniger als zwei Zeichen nicht an', async () => {
      await typeQuery('S');

      httpMock.expectNone(req => req.url === `${API}/banks/search`);
      expect(component.bankResults()).toEqual([]);
    });

    it('geht nach Auswahl einer Bank zu den Zugangsdaten', async () => {
      await typeQuery('Sparkasse');
      httpMock.expectOne(req => req.url === `${API}/banks/search`).flush([SPARKASSE]);
      fixture.detectChanges();

      (query('bank-results')?.querySelector('button') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(component.step()).toBe('credentials');
      expect(component.bank()).toEqual(SPARKASSE);
      expect(query('step-credentials')).toBeTruthy();
    });

    it('Fehlerfall: gescheiterte Suche bleibt auf Schritt 1 und meldet den Fehler', async () => {
      await typeQuery('Sparkasse');

      httpMock
        .expectOne(req => req.url === `${API}/banks/search`)
        .flush('boom', { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(component.step()).toBe('bank-search');
      expect(component.error()?.code).toBe('SEARCH_FAILED');
      expect(query('wizard-error')).toBeTruthy();
    });
  });

  // ==================================================================
  // SCHRITT 2: ZUGANGSDATEN
  // ==================================================================

  describe('Schritt 2: Zugangsdaten', () => {
    beforeEach(() => goToCredentials());

    it('sendet BLZ, Login-ID und PIN an /bank-connections', () => {
      component.loginId.set('1234567');
      component.pin.set('geheim');
      component.submitCredentials();

      const request = httpMock.expectOne(`${API}/bank-connections`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({
        blz: '50050201',
        loginId: '1234567',
        pin: 'geheim',
      });

      request.flush({ connectionId: 'conn-1', tanMethods: [CHIPTAN] });
    });

    it('sendet ohne vollständige Eingaben nichts', () => {
      component.loginId.set('1234567');
      component.pin.set('');
      component.submitCredentials();

      httpMock.expectNone(`${API}/bank-connections`);
    });

    it('zeigt bei mehreren TAN-Verfahren die Auswahl', () => {
      submitCredentials([CHIPTAN, PUSHTAN]);

      expect(component.step()).toBe('tan-method');
      expect(query('step-tan-method')).toBeTruthy();
    });

    it('überspringt die Auswahl, wenn die Bank nur ein Verfahren anbietet', () => {
      submitCredentials([CHIPTAN]);

      expect(component.step()).toBe('tan-entry');
      expect(component.tanMethod()).toEqual(CHIPTAN);
    });

    it('Fehlerfall: falsche PIN bleibt auf Schritt 2 und leert das PIN-Feld', () => {
      component.loginId.set('1234567');
      component.pin.set('falsch');
      component.submitCredentials();

      httpMock
        .expectOne(`${API}/bank-connections`)
        .flush({ code: 'INVALID_PIN' }, { status: 401, statusText: 'Unauthorized' });
      fixture.detectChanges();

      expect(component.step()).toBe('credentials');
      expect(component.error()?.code).toBe('INVALID_PIN');
      expect(component.pin()).toBe('');
      expect(query('wizard-error')?.textContent).toContain('PIN');
    });

    it('Fehlerfall: Bank nicht erreichbar', () => {
      component.loginId.set('1234567');
      component.pin.set('geheim');
      component.submitCredentials();

      httpMock
        .expectOne(`${API}/bank-connections`)
        .flush(null, { status: 503, statusText: 'Service Unavailable' });
      fixture.detectChanges();

      expect(component.step()).toBe('credentials');
      expect(component.error()?.code).toBe('BANK_UNREACHABLE');
      expect(component.busy()).toBe(false);
    });
  });

  // ==================================================================
  // SCHRITT 3: TAN-VERFAHREN
  // ==================================================================

  describe('Schritt 3: TAN-Verfahren wählen', () => {
    beforeEach(() => {
      goToCredentials();
      submitCredentials([CHIPTAN, PUSHTAN]);
    });

    it('zeigt für jedes Verfahren einen Radio-Button', () => {
      const radios = fixture.nativeElement.querySelectorAll('input[type="radio"]');
      expect(radios.length).toBe(2);
      expect(query('step-tan-method')?.textContent).toContain('chipTAN');
      expect(query('step-tan-method')?.textContent).toContain('pushTAN');
    });

    it('geht erst nach Bestätigung der Auswahl zur TAN-Eingabe', () => {
      component.selectTanMethod(PUSHTAN);
      fixture.detectChanges();
      expect(component.step()).toBe('tan-method');

      component.confirmTanMethod();
      fixture.detectChanges();

      expect(component.step()).toBe('tan-entry');
      expect(component.tanMethod()).toEqual(PUSHTAN);
    });

    it('bleibt stehen, solange nichts gewählt wurde', () => {
      component.confirmTanMethod();
      expect(component.step()).toBe('tan-method');
    });

    it('führt über Zurück wieder zu den Zugangsdaten', () => {
      component.back();
      fixture.detectChanges();

      expect(component.step()).toBe('credentials');
      expect(query('step-credentials')).toBeTruthy();
    });
  });

  // ==================================================================
  // SCHRITT 4: TAN EINGEBEN
  // ==================================================================

  describe('Schritt 4: TAN eingeben', () => {
    beforeEach(() => goToTanEntry());

    it('zeigt den Hinweistext passend zum Verfahren', () => {
      expect(query('tan-hint')?.textContent).toContain('chipTAN-App');
    });

    it('bevorzugt den Hinweistext der Bank', () => {
      component.restart();
      fixture.detectChanges();
      goToCredentials();
      submitCredentials([PUSHTAN]);

      expect(query('tan-hint')?.textContent).toContain('Schau in deine Banking-App.');
    });

    it('bestätigt die TAN und landet auf dem Erfolgsscreen', () => {
      component.tan.set('123456');
      component.submitTan();

      const request = httpMock.expectOne(`${API}/bank-connections/conn-1/confirm-tan`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ tanMethodId: 'chipTAN', tan: '123456' });

      request.flush(ACCOUNTS);
      fixture.detectChanges();

      expect(component.step()).toBe('success');
    });

    it('sendet ohne TAN nichts', () => {
      component.tan.set('   ');
      component.submitTan();

      httpMock.expectNone(`${API}/bank-connections/conn-1/confirm-tan`);
    });

    it('Fehlerfall: falsche TAN bleibt auf Schritt 4', () => {
      component.tan.set('000000');
      component.submitTan();

      httpMock
        .expectOne(`${API}/bank-connections/conn-1/confirm-tan`)
        .flush({ code: 'TAN_INVALID' }, { status: 400, statusText: 'Bad Request' });
      fixture.detectChanges();

      expect(component.step()).toBe('tan-entry');
      expect(component.error()?.code).toBe('TAN_INVALID');
      expect(query('step-tan-entry')).toBeTruthy();
    });

    it('Fehlerfall: abgelaufene TAN wirft zurück auf die Anmeldung', () => {
      component.tan.set('123456');
      component.submitTan();

      httpMock
        .expectOne(`${API}/bank-connections/conn-1/confirm-tan`)
        .flush({ code: 'TAN_EXPIRED' }, { status: 410, statusText: 'Gone' });
      fixture.detectChanges();

      expect(component.step()).toBe('credentials');
      expect(component.error()?.code).toBe('TAN_EXPIRED');
      // Die Bank-Session ist verfallen, sie darf nicht weiterverwendet werden
      expect(component.tanMethod()).toBeNull();
      expect(query('step-credentials')).toBeTruthy();
    });

    it('Fehlerfall: Bank während der TAN-Prüfung nicht erreichbar', () => {
      component.tan.set('123456');
      component.submitTan();

      httpMock
        .expectOne(`${API}/bank-connections/conn-1/confirm-tan`)
        .flush(null, { status: 0, statusText: 'Unknown Error' });
      fixture.detectChanges();

      expect(component.step()).toBe('tan-entry');
      expect(component.error()?.code).toBe('BANK_UNREACHABLE');
    });
  });

  // ==================================================================
  // SCHRITT 5: ERFOLGSSCREEN
  // ==================================================================

  describe('Schritt 5: Erfolgsscreen', () => {
    beforeEach(() => {
      goToTanEntry();
      component.tan.set('123456');
      component.submitTan();
      httpMock.expectOne(`${API}/bank-connections/conn-1/confirm-tan`).flush(ACCOUNTS);
      fixture.detectChanges();
    });

    it('listet jedes gefundene Konto auf', () => {
      const rows = fixture.nativeElement.querySelectorAll('.account-row');
      expect(rows.length).toBe(2);
    });

    it('zeigt IBAN maskiert, Kontotyp und Saldo', () => {
      const text = query('account-list')?.textContent ?? '';

      expect(text).toContain('DE89 •••• 3000');
      expect(text).not.toContain('DE89370400440532013000');
      expect(text).toContain('Girokonto');
      expect(text).toContain('1,420.50');
      expect(text).toContain('-35.00');
    });

    it('startet über den Neustart wieder bei der Banksuche', () => {
      (query('restart') as HTMLButtonElement).click();
      fixture.detectChanges();

      expect(component.step()).toBe('bank-search');
      expect(component.accounts()).toEqual([]);
      expect(component.bank()).toBeNull();
      expect(query('step-bank-search')).toBeTruthy();
    });
  });

  // ==================================================================
  // IBAN-MASKIERUNG
  // ==================================================================

  describe('maskIban', () => {
    it('zeigt nur die ersten und letzten vier Stellen', () => {
      expect(maskIban('DE89370400440532013000')).toBe('DE89 •••• 3000');
    });

    it('entfernt Leerzeichen aus der Eingabe', () => {
      expect(maskIban('DE89 3704 0044 0532 0130 00')).toBe('DE89 •••• 3000');
    });

    it('lässt zu kurze Werte unverändert', () => {
      expect(maskIban('DE89')).toBe('DE89');
    });
  });
});
