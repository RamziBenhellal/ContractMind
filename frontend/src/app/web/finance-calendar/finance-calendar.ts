import { Component, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ContractService } from '../../service/contract/contract';
import { IncomeService } from '../../service/income/income';
import { Contract } from '../../service/contract/contract';
import { Income } from '../../service/income/income';

// Hilfs-Interface für unsere Timeline
interface TimelineDay {
  day: number;
  incomes: Income[];
  contracts: Contract[];
  dailyBalance: number; // Was passiert an genau diesem Tag? (+ oder -)
  runningTotal: number; // Wie viel Geld ist nach diesem Tag noch da?
}

@Component({
  selector: 'app-finance-calendar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './finance-calendar.html',
  styleUrls: ['./finance-calendar.css']
})
export class FinanceCalendarComponent implements OnInit {
  private contractService = inject(ContractService);
  private incomeService = inject(IncomeService);
  private cdr = inject(ChangeDetectorRef);

  timelineDays: TimelineDay[] = [];
  totalIncome = 0;
  totalExpenses = 0;
  monthlyRemaining = 0;

  ngOnInit() {
    this.loadTimelineData();
  }

  loadTimelineData() {
    // In einer echten App würde man hier forkJoin nutzen, um auf beide Requests zu warten.
    // Der Einfachheit halber verschachteln wir es hier kurz:
    this.incomeService.getIncomes().subscribe(incomes => {
      this.contractService.getContracts().subscribe(contracts => {
        this.buildTimeline(incomes, contracts);
      });
    });
  }

  buildTimeline(incomes: Income[], contracts: Contract[]) {
    // 1. Gesamtsummen berechnen
    this.totalIncome = incomes.reduce((sum, i) => sum + (i.amount || 0), 0);

    // Nur verifizierte Verträge einbeziehen
    const activeContracts = contracts.filter(c => c.status === 'VERIFIED');
    this.totalExpenses = activeContracts.reduce((sum, c) => sum + (c.monthlyCost || 0), 0);
    this.monthlyRemaining = this.totalIncome - this.totalExpenses;

    // 2. Ein Dictionary für alle Tage erstellen (1 bis 31)
    const daysMap = new Map<number, TimelineDay>();

    // Einkommen einsortieren
    incomes.forEach(income => {
      const day = income.paydayOfMonth || 1; // Falls leer, nimm den 1.
      if (!daysMap.has(day)) this.createNewDay(daysMap, day);
      daysMap.get(day)!.incomes.push(income);
      if(income.amount)
      daysMap.get(day)!.dailyBalance += income.amount;
    });

    // Verträge einsortieren
    activeContracts.forEach(contract => {
      const day = contract.dueDayOfMonth || 1;
      if (!daysMap.has(day)) this.createNewDay(daysMap, day);
      daysMap.get(day)!.contracts.push(contract);
      daysMap.get(day)!.dailyBalance -= contract.monthlyCost;
    });

    // 3. Map in ein Array umwandeln und nach Tagen (1-31) sortieren
    this.timelineDays = Array.from(daysMap.values()).sort((a, b) => a.day - b.day);

    // 4. Den laufenden Saldo berechnen (Running Total)
    let currentBalance = 0;
    this.timelineDays.forEach(tDay => {
      currentBalance += tDay.dailyBalance;
      tDay.runningTotal = currentBalance;
    });

    this.cdr.detectChanges();
  }

  private createNewDay(map: Map<number, TimelineDay>, day: number) {
    map.set(day, {
      day: day,
      incomes: [],
      contracts: [],
      dailyBalance: 0,
      runningTotal: 0
    });
  }
}
