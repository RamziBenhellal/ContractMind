import { Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, filter, of, switchMap, tap } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  Bank,
  BankConnectionError,
  BankConnectionService,
  TanMethod,
} from '../../../service/bankconnection/bankconnection';
import {
  WizardEvent,
  WizardState,
  WizardStep,
  initialWizardState,
  wizardReducer,
} from './bank-connection-wizard.machine';

export const BANK_SEARCH_DEBOUNCE_MS = 300;
export const BANK_SEARCH_MIN_LENGTH = 2;

/** Fallback-Hinweise, falls die Bank keinen eigenen Text mitschickt */
const TAN_HINTS: Record<string, string> = {
  chiptan: 'Bestätige die Anfrage in deiner chipTAN-App und gib die angezeigte TAN ein.',
  pushtan: 'Die Freigabe kommt nach der Auswahl in deine S-pushTAN-App.',
  smstan: 'Wir haben dir eine TAN per SMS geschickt.',
  phototan: 'Scanne die Grafik in deiner photoTAN-App und gib die angezeigte TAN ein.',
};

const GENERIC_TAN_HINT = 'Gib die TAN ein, die dir deine Bank für diesen Auftrag bereitstellt.';

/** Zeigt nur Land, Prüfziffer und die letzten vier Stellen */
export function maskIban(iban: string): string {
  const compact = (iban ?? '').replace(/\s+/g, '');
  if (compact.length <= 8) return compact;
  return `${compact.slice(0, 4)} •••• ${compact.slice(-4)}`;
}

@Component({
  selector: 'app-bank-connection-wizard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bank-connection-wizard.html',
  styleUrls: ['./bank-connection-wizard.css'],
})
export class BankConnectionWizardComponent {
  private service = inject(BankConnectionService);

  private readonly state = signal<WizardState>(initialWizardState);

  readonly step = computed(() => this.state().step);
  readonly busy = computed(() => this.state().busy);
  readonly error = computed(() => this.state().error);
  readonly bank = computed(() => this.state().bank);
  readonly tanMethods = computed(() => this.state().tanMethods);
  readonly tanMethod = computed(() => this.state().tanMethod);
  readonly accounts = computed(() => this.state().accounts);

  readonly stepOrder: WizardStep[] = ['bank-search', 'credentials', 'tan-method', 'tan-entry', 'success'];
  readonly stepLabels: Record<WizardStep, string> = {
    'bank-search': 'Bank',
    credentials: 'Anmeldung',
    'tan-method': 'Verfahren',
    'tan-entry': 'TAN',
    success: 'Fertig',
  };
  readonly currentStepIndex = computed(() => this.stepOrder.indexOf(this.step()));

  // --- Schritt 1: Banksuche ---
  readonly bankQuery = signal('');
  readonly bankResults = signal<Bank[]>([]);
  readonly searching = signal(false);
  private readonly queryInput = new Subject<string>();

  // --- Schritt 2: Zugangsdaten ---
  readonly loginId = signal('');
  readonly pin = signal('');
  readonly credentialsValid = computed(
    () => this.loginId().trim().length > 0 && this.pin().trim().length > 0
  );

  // --- Schritt 3: Auswahl, bestätigt wird sie erst mit "Weiter" ---
  readonly pendingTanMethod = signal<TanMethod | null>(null);

  // --- Schritt 4: TAN ---
  readonly tan = signal('');
  readonly tanDecoupled = signal(false);
  readonly challengeHint = signal<string | null>(null);
  readonly tanValid = computed(() => this.tanDecoupled() || this.tan().trim().length > 0);

  readonly tanHint = computed(() => {
    if (this.challengeHint()) return this.challengeHint() as string;
    const method = this.tanMethod();
    if (!method) return GENERIC_TAN_HINT;
    if (method.hint) return method.hint;

    const key = `${method.id ?? ''}`.toLowerCase().replace(/[^a-z]/g, '');
    return TAN_HINTS[key] ?? GENERIC_TAN_HINT;
  });

  readonly maskIban = maskIban;

  constructor() {
    this.queryInput
      .pipe(
        debounceTime(BANK_SEARCH_DEBOUNCE_MS),
        distinctUntilChanged(),
        filter(query => query.trim().length >= BANK_SEARCH_MIN_LENGTH),
        tap(() => this.searching.set(true)),
        switchMap(query =>
          this.service.searchBanks(query.trim()).pipe(
            catchError((error: unknown) => {
              this.dispatch({ type: 'FAIL', error: this.asWizardError(error) });
              return of([] as Bank[]);
            })
          )
        ),
        takeUntilDestroyed()
      )
      .subscribe(banks => {
        this.bankResults.set(banks);
        this.searching.set(false);
      });
  }

  // ==========================================
  // SCHRITT 1: BANK SUCHEN
  // ==========================================

  onQueryChange(query: string) {
    this.bankQuery.set(query);
    if (query.trim().length < BANK_SEARCH_MIN_LENGTH) {
      this.bankResults.set([]);
    }
    this.queryInput.next(query);
  }

  selectBank(bank: Bank) {
    this.dispatch({ type: 'SELECT_BANK', bank });
  }

  // ==========================================
  // SCHRITT 2: ZUGANGSDATEN
  // ==========================================

  submitCredentials() {
    const bank = this.bank();
    if (!bank || this.busy() || !this.credentialsValid()) return;

    this.dispatch({ type: 'SUBMIT' });
    this.service
      .startConnection({ blz: bank.blz, loginId: this.loginId().trim(), pin: this.pin() })
      .subscribe({
        next: response => {
          this.pin.set(''); // PIN nicht länger als nötig im Speicher halten
          this.dispatch({
            type: 'CONNECTION_STARTED',
            connectionId: response.connectionId,
            tanMethods: response.tanMethods,
          });
          const methods = response.tanMethods ?? [];
          if (methods.length === 1) {
            this.triggerTanChallenge(response.connectionId, methods[0]);
          }
        },
        error: (error: unknown) => {
          this.pin.set('');
          this.dispatch({ type: 'FAIL', error: this.asWizardError(error) });
        },
      });
  }

  // ==========================================
  // SCHRITT 3: TAN-VERFAHREN
  // ==========================================

  selectTanMethod(tanMethod: TanMethod) {
    this.pendingTanMethod.set(tanMethod);
  }

  confirmTanMethod() {
    const tanMethod = this.pendingTanMethod();
    const connectionId = this.state().connectionId;
    if (!tanMethod || !connectionId || this.busy()) return;
    this.dispatch({ type: 'SELECT_TAN_METHOD', tanMethod });
    this.triggerTanChallenge(connectionId, tanMethod);
  }

  // ==========================================
  // SCHRITT 4: TAN BESTÄTIGEN
  // ==========================================

  submitTan() {
    const { connectionId, tanMethod } = this.state();
    if (!connectionId || this.busy() || !this.tanValid()) return;

    this.dispatch({ type: 'SUBMIT' });
    this.service.confirmTan(connectionId, tanMethod?.id ?? '', this.tan().trim()).subscribe({
      next: accounts => {
        this.tan.set('');
        this.dispatch({ type: 'TAN_CONFIRMED', accounts });
      },
      error: (error: unknown) => {
        this.tan.set('');
        this.dispatch({ type: 'FAIL', error: this.asWizardError(error) });
      },
    });
  }

  // ==========================================
  // NAVIGATION
  // ==========================================

  back() {
    this.dispatch({ type: 'BACK' });
  }

  restart() {
    this.bankQuery.set('');
    this.loginId.set('');
    this.pin.set('');
    this.tan.set('');
    this.tanDecoupled.set(false);
    this.challengeHint.set(null);
    this.pendingTanMethod.set(null);
    this.bankResults.set([]);
    this.dispatch({ type: 'RESET' });
  }

  private triggerTanChallenge(connectionId: string, tanMethod: TanMethod) {
    this.dispatch({ type: 'SUBMIT' });
    this.service.selectTanMethod(connectionId, tanMethod.id).subscribe({
      next: challenge => {
        this.tanDecoupled.set(!!challenge.decoupled);
        this.challengeHint.set(challenge.hint || challenge.challenge || null);
        this.dispatch({ type: 'CHALLENGE_READY' });
      },
      error: (error: unknown) => {
        this.dispatch({ type: 'FAIL', error: this.asWizardError(error) });
      },
    });
  }

  private dispatch(event: WizardEvent) {
    this.state.update(current => wizardReducer(current, event));
  }

  /** Alles, was nicht als fachlicher Fehler ankommt, wird zu UNKNOWN */
  private asWizardError(error: unknown): BankConnectionError {
    const candidate = error as BankConnectionError | null;
    if (candidate && typeof candidate.code === 'string' && typeof candidate.message === 'string') {
      return candidate;
    }
    return { code: 'UNKNOWN', message: 'Es ist ein unerwarteter Fehler aufgetreten.' };
  }
}
