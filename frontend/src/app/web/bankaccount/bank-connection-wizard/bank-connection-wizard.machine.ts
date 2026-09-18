import {
  Bank,
  BankConnectionError,
  BankConnectionErrorCode,
  ConnectedAccount,
  TanMethod,
} from '../../../service/bankconnection/bankconnection';

export type WizardStep = 'bank-search' | 'credentials' | 'tan-method' | 'tan-entry' | 'success';

export interface WizardState {
  step: WizardStep;
  /** Ein Request läuft - blockiert Doppel-Submits */
  busy: boolean;
  error: BankConnectionError | null;
  bank: Bank | null;
  connectionId: string | null;
  tanMethods: TanMethod[];
  tanMethod: TanMethod | null;
  accounts: ConnectedAccount[];
}

export type WizardEvent =
  | { type: 'SELECT_BANK'; bank: Bank }
  | { type: 'SUBMIT' }
  | { type: 'CONNECTION_STARTED'; connectionId: string; tanMethods: TanMethod[] }
  | { type: 'SELECT_TAN_METHOD'; tanMethod: TanMethod }
  | { type: 'TAN_CONFIRMED'; accounts: ConnectedAccount[] }
  | { type: 'FAIL'; error: BankConnectionError }
  | { type: 'BACK' }
  | { type: 'RESET' };

export const initialWizardState: WizardState = {
  step: 'bank-search',
  busy: false,
  error: null,
  bank: null,
  connectionId: null,
  tanMethods: [],
  tanMethod: null,
  accounts: [],
};

/**
 * Wohin der Nutzer nach einem Fehler geschickt wird. Fehler, die er auf dem
 * aktuellen Schritt selbst beheben kann, lassen ihn dort stehen.
 */
function stepAfterError(state: WizardState, code: BankConnectionErrorCode): WizardStep {
  switch (code) {
    case 'INVALID_PIN':
      return 'credentials';
    case 'TAN_EXPIRED':
      // Die Bank-Session ist verfallen, der Login muss komplett neu laufen
      return 'credentials';
    case 'TAN_INVALID':
      return 'tan-entry';
    case 'SEARCH_FAILED':
      return 'bank-search';
    default:
      return state.step;
  }
}

function previousStep(state: WizardState): WizardStep {
  switch (state.step) {
    case 'credentials':
      return 'bank-search';
    case 'tan-method':
      return 'credentials';
    case 'tan-entry':
      // Zurück zur Auswahl nur, wenn es überhaupt etwas zu wählen gab
      return state.tanMethods.length > 1 ? 'tan-method' : 'credentials';
    default:
      return state.step;
  }
}

/**
 * Die Übergangstabelle des Wizards. Events, die im aktuellen Schritt nicht
 * vorgesehen sind, lassen den Zustand unverändert.
 */
export function wizardReducer(state: WizardState, event: WizardEvent): WizardState {
  switch (event.type) {
    case 'SELECT_BANK':
      if (state.step !== 'bank-search') return state;
      return { ...state, bank: event.bank, step: 'credentials', error: null };

    case 'SUBMIT':
      if (state.busy) return state;
      return { ...state, busy: true, error: null };

    case 'CONNECTION_STARTED': {
      if (state.step !== 'credentials') return state;
      const tanMethods = event.tanMethods ?? [];
      return {
        ...state,
        busy: false,
        error: null,
        connectionId: event.connectionId,
        tanMethods,
        // Bietet die Bank nur ein Verfahren an, ist Schritt 3 überflüssig
        tanMethod: tanMethods.length === 1 ? tanMethods[0] : null,
        step: tanMethods.length > 1 ? 'tan-method' : 'tan-entry',
      };
    }

    case 'SELECT_TAN_METHOD':
      if (state.step !== 'tan-method') return state;
      return { ...state, tanMethod: event.tanMethod, step: 'tan-entry', error: null };

    case 'TAN_CONFIRMED':
      if (state.step !== 'tan-entry') return state;
      return { ...state, busy: false, error: null, accounts: event.accounts, step: 'success' };

    case 'FAIL': {
      const sessionLost = event.error.code === 'TAN_EXPIRED';
      return {
        ...state,
        busy: false,
        error: event.error,
        step: stepAfterError(state, event.error.code),
        connectionId: sessionLost ? null : state.connectionId,
        tanMethods: sessionLost ? [] : state.tanMethods,
        tanMethod: sessionLost ? null : state.tanMethod,
      };
    }

    case 'BACK':
      if (state.busy) return state;
      return { ...state, step: previousStep(state), error: null };

    case 'RESET':
      return { ...initialWizardState };

    default:
      return state;
  }
}
