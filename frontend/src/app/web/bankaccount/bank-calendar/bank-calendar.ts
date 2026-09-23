import { Component, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { BankAccountService, BankAccount } from '../../../service/bankaccount/bankaccount';

interface CalendarDay {
  date: Date;
  dayOfMonth: number;
  isCurrentMonth: boolean;
  dateString: string; // Format: YYYY-MM-DD
  manualBalance?: number; // Wenn der Nutzer an diesem Tag einen Stand eingetragen hat
  projectedBalance: number; // Der Kontostand, der an diesem Tag gilt
}

@Component({
  selector: 'app-bank-calendar',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './bank-calendar.html',
  styleUrls: ['./bank-calendar.css']
})
export class BankCalendarComponent implements OnInit {
  private bankAccountService = inject(BankAccountService);
  private cdr = inject(ChangeDetectorRef);

  // --- State: Daten ---
  accounts: BankAccount[] = [];
  selectedAccount: BankAccount | null = null;

  // --- State: Kalender ---
  currentYear: number = new Date().getFullYear();
  currentMonth: number = new Date().getMonth(); // 0 = Jan, 11 = Dez
  monthNames = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
  weekDays = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'];
  calendarGrid: CalendarDay[] = [];

  // --- State: Formulare ---
  isAddAccountModalOpen = false;
  newAccount: BankAccount ={
  accountName: '',
  bankName : ''
  } ;



  isUpdateBalanceModalOpen = false;
  updateBalanceDate = new Date().toISOString().split('T')[0];
  updateBalanceAmount = 0;

  ngOnInit() {
    this.loadAllData();
  }

  loadAllData() {
    this.bankAccountService.getBankAccounts().subscribe(accs => {
      this.accounts = accs;
      if (this.accounts.length > 0) this.selectedAccount = this.accounts[0];

      this.generateCalendar();
    });
  }

  // ==========================================
  // KALENDER & KONTOSTAND LOGIK
  // ==========================================

  changeMonth(delta: number) {
    this.currentMonth += delta;
    if (this.currentMonth > 11) {
      this.currentMonth = 0;
      this.currentYear++;
    } else if (this.currentMonth < 0) {
      this.currentMonth = 11;
      this.currentYear--;
    }
    this.generateCalendar();
  }

  generateCalendar() {
    this.calendarGrid = [];
    const firstDayOfMonth = new Date(this.currentYear, this.currentMonth, 1);
    const lastDayOfMonth = new Date(this.currentYear, this.currentMonth + 1, 0);

    // Wochentag des 1. (Montag = 1, Sonntag = 0 -> wir wandeln Sonntag zu 7 um)
    let startDayOfWeek = firstDayOfMonth.getDay();
    if (startDayOfWeek === 0) startDayOfWeek = 7;

    // Vorherige Tage (Grau im Kalender)
    for (let i = startDayOfWeek - 1; i > 0; i--) {
      const d = new Date(this.currentYear, this.currentMonth, 1 - i);
      this.calendarGrid.push(this.createCalendarDay(d, false));
    }

    // Aktueller Monat
    for (let i = 1; i <= lastDayOfMonth.getDate(); i++) {
      const d = new Date(this.currentYear, this.currentMonth, i);
      this.calendarGrid.push(this.createCalendarDay(d, true));
    }

    // Auffüllen bis zum Ende der Woche
    const remainingDays = 42 - this.calendarGrid.length; // 6 Wochen Grid
    for (let i = 1; i <= remainingDays; i++) {
      const d = new Date(this.currentYear, this.currentMonth + 1, i);
      this.calendarGrid.push(this.createCalendarDay(d, false));
    }

    this.calculateProjections();
  }

  private createCalendarDay(date: Date, isCurrentMonth: boolean): CalendarDay {
    // Wandelt Date in 'YYYY-MM-DD' sicher unter Beachtung der Zeitzone um
    const dateString = new Date(date.getTime() - (date.getTimezoneOffset() * 60000)).toISOString().split('T')[0];

    return {
      date,
      dayOfMonth: date.getDate(),
      isCurrentMonth,
      dateString,
      projectedBalance: 0
    };
  }

  private calculateProjections() {
    if (!this.selectedAccount || this.calendarGrid.length === 0) return;

    const history = this.selectedAccount.balanceHistory || {};
    let runningBalance = 0;

    // 1. Den echten Startwert finden (letzter bekannter Kontostand VOR unserem Kalender-Grid)
    const calendarStartDate = this.calendarGrid[0].dateString;
    const historyDates = Object.keys(history).sort(); // Datum-Strings chronologisch sortieren

    for (let d of historyDates) {
      if (d < calendarStartDate) {
        runningBalance = history[d]; // Wir überschreiben es, bis wir das aktuellste Datum vor dem Kalender haben
      }
    }

    // 2. Jetzt iterieren wir durch unser Kalender-Grid
    for (let day of this.calendarGrid) {

      // Nur ein erfasster Kontostand verändert den Saldo - dazwischen bleibt er unverändert stehen
      if (history[day.dateString] !== undefined) {
        runningBalance = history[day.dateString];
        day.manualBalance = runningBalance;
      } else {
        day.manualBalance = undefined;
      }

      day.projectedBalance = runningBalance;
    }

    this.cdr.detectChanges();
  }

  // ==========================================
  // KONTO AKTIONEN
  // ==========================================

  selectAccount(account: BankAccount) {
    this.selectedAccount = account;
    this.calculateProjections();
  }

  saveNewAccount() {
    if (!this.newAccount) return;
    this.bankAccountService.addBankAccount(this.newAccount).subscribe(acc => {
      this.accounts.push(acc);
      this.selectedAccount = acc;
      this.isAddAccountModalOpen = false;
      this.newAccount.accountName = acc.accountName;
      this.newAccount.bankName = acc.bankName;
      this.calculateProjections();
    });
  }

  saveBalanceRecord() {
    if (!this.selectedAccount || !this.selectedAccount.id) return;
    this.bankAccountService.recordBalance(this.selectedAccount.id, this.updateBalanceDate, this.updateBalanceAmount)
      .subscribe(updatedAcc => {
        // Aktualisiere das Konto im lokalen Array
        const idx = this.accounts.findIndex(a => a.id === updatedAcc.id);
        if (idx !== -1) this.accounts[idx] = updatedAcc;
        this.selectedAccount = updatedAcc;

        this.isUpdateBalanceModalOpen = false;
        this.generateCalendar(); // Raster neu berechnen, da sich die Basis geändert hat
      });
  }
}
