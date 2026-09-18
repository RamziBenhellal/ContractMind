import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Contract, ContractService } from '../../../service/contract/contract';
import { Income, IncomeService } from '../../../service/income/income';
import { PendingReviewTransaction } from '../../../service/transaction/transaction';
import { TransactionReviewStore } from '../../../service/transaction/transaction-review.store';

export interface MatchOption {
  id: number;
  label: string;
  kind: 'contract' | 'income';
}

export const NORMAL_CATEGORIES = [
  'Lebensmittel',
  'Shopping',
  'Transport',
  'Freizeit',
  'Überweisung',
  'Sonstiges',
];

@Component({
  selector: 'app-transaction-review',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './transaction-review.html',
  styleUrls: ['./transaction-review.css'],
})
export class TransactionReviewComponent implements OnInit, OnDestroy {
  readonly store = inject(TransactionReviewStore);
  private readonly contractService = inject(ContractService);
  private readonly incomeService = inject(IncomeService);

  readonly matchOptions = signal<MatchOption[]>([]);
  readonly selectedMatchId = signal<Record<number, number | null>>({});
  readonly selectedCategory = signal<Record<number, string>>({});
  readonly newEntityOpenFor = signal<number | null>(null);
  readonly newEntityKind = signal<'NEW_CONTRACT' | 'NEW_INCOME'>('NEW_CONTRACT');
  readonly newEntityForm = signal({
    provider: '',
    contractType: '',
    monthlyCost: 0,
    dueDayOfMonth: 1,
  });
  readonly categories = NORMAL_CATEGORIES;
  readonly submittingId = signal<number | null>(null);

  ngOnInit(): void {
    this.store.start();
    this.loadMatchOptions();
  }

  ngOnDestroy(): void {
    this.store.stop();
  }

  displayName(tx: PendingReviewTransaction): string {
    return tx.counterpartyName || tx.purpose || 'Unbekannter Umsatz';
  }

  selectedMatchLabel(tx: PendingReviewTransaction): string {
    const id = this.selectedMatchId()[tx.id];
    return this.matchOptions().find(option => option.id === id)?.label ?? 'Vertragsname';
  }

  onMatchChange(txId: number, rawId: string): void {
    const id = Number(rawId);
    this.selectedMatchId.update(current => ({ ...current, [txId]: Number.isNaN(id) ? null : id }));
  }

  onCategoryChange(txId: number, category: string): void {
    this.selectedCategory.update(current => ({ ...current, [txId]: category }));
  }

  categoryFor(txId: number): string {
    return this.selectedCategory()[txId] || '';
  }

  patchNewEntity(field: 'provider' | 'monthlyCost' | 'dueDayOfMonth', value: string | number): void {
    this.newEntityForm.update(form => ({ ...form, [field]: value }));
  }

  async confirmExistingMatch(tx: PendingReviewTransaction): Promise<void> {
    const targetEntityId = this.selectedMatchId()[tx.id];
    if (!targetEntityId) {
      return;
    }
    this.submittingId.set(tx.id);
    try {
      await this.store.submitFeedback(tx.id, {
        decision: 'EXISTING_MATCH',
        targetEntityId,
      });
    } finally {
      this.submittingId.set(null);
    }
  }

  openNewEntityForm(tx: PendingReviewTransaction): void {
    const amount = Math.abs(tx.amount ?? 0);
    const kind = tx.amount >= 0 && !tx.suggestNewContract ? 'NEW_INCOME' : 'NEW_CONTRACT';
    const dueDay = tx.bookingDate ? Number(tx.bookingDate.slice(8, 10)) || 1 : 1;
    this.newEntityKind.set(kind);
    this.newEntityForm.set({
      provider: this.displayName(tx),
      contractType: '',
      monthlyCost: amount,
      dueDayOfMonth: dueDay,
    });
    this.newEntityOpenFor.set(tx.id);
  }

  closeNewEntityForm(): void {
    this.newEntityOpenFor.set(null);
  }

  async submitNewEntity(tx: PendingReviewTransaction): Promise<void> {
    const form = this.newEntityForm();
    this.submittingId.set(tx.id);
    try {
      await this.store.submitFeedback(tx.id, {
        decision: this.newEntityKind(),
        newContractData: {
          provider: form.provider,
          contractType: form.contractType || undefined,
          monthlyCost: form.monthlyCost,
          dueDayOfMonth: form.dueDayOfMonth,
        },
      });
      this.closeNewEntityForm();
    } finally {
      this.submittingId.set(null);
    }
  }

  async confirmNormal(tx: PendingReviewTransaction): Promise<void> {
    this.submittingId.set(tx.id);
    try {
      await this.store.submitFeedback(tx.id, {
        decision: 'NORMAL',
        category: this.selectedCategory()[tx.id] || undefined,
      });
    } finally {
      this.submittingId.set(null);
    }
  }

  private loadMatchOptions(): void {
    this.contractService.getContracts().subscribe(contracts => {
      this.incomeService.getIncomes().subscribe(incomes => {
        this.matchOptions.set([
          ...contracts.filter((item): item is Contract & { id: number } => !!item.id).map(item => ({
            id: item.id,
            label: item.provider,
            kind: 'contract' as const,
          })),
          ...incomes.filter((item): item is Income & { id: number } => !!item.id).map(item => ({
            id: item.id,
            label: item.source || 'Einkommen',
            kind: 'income' as const,
          })),
        ]);
      });
    });
  }
}
