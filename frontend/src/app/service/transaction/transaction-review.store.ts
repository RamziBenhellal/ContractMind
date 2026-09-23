import { inject, Injectable, InjectionToken, signal } from '@angular/core';
import { interval, Subscription } from 'rxjs';
import {
  PendingReviewTransaction,
  TransactionFeedbackRequest,
  TransactionService,
} from './transaction';

export const REVIEW_POLL_INTERVAL_MS = new InjectionToken<number>('REVIEW_POLL_INTERVAL_MS', {
  providedIn: 'root',
  factory: () => 8000,
});

@Injectable({ providedIn: 'root' })
export class TransactionReviewStore {
  private readonly transactionService = inject(TransactionService);
  private readonly pollIntervalMs = inject(REVIEW_POLL_INTERVAL_MS);
  private polling?: Subscription;

  readonly items = signal<PendingReviewTransaction[]>([]);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  start(): void {
    this.refresh();
    if (this.polling || this.pollIntervalMs <= 0) {
      return;
    }
    this.polling = interval(this.pollIntervalMs).subscribe(() => this.refresh());
  }

  stop(): void {
    this.polling?.unsubscribe();
    this.polling = undefined;
  }

  refresh(): void {
    this.loading.set(true);
    this.transactionService.getPendingReview().subscribe({
      next: items => {
        this.items.set(items ?? []);
        this.error.set(null);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Review-Queue konnte nicht geladen werden.');
        this.loading.set(false);
      },
    });
  }

  submitFeedback(id: number, body: TransactionFeedbackRequest) {
    return new Promise<void>((resolve, reject) => {
      this.transactionService.submitFeedback(id, body).subscribe({
        next: () => {
          this.items.update(list => list.filter(item => item.id !== id));
          resolve();
        },
        error: err => {
          this.error.set('Feedback konnte nicht gespeichert werden.');
          reject(err);
        },
      });
    });
  }
}
