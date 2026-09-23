import { Component, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

// Services & Models
import { AuthService } from '../../service/auth/auth';
import { Contract, ContractService } from '../../service/contract/contract';
import { Income, IncomeService } from '../../service/income/income';

// Components
import { FinanceCalendarComponent } from '../finance-calendar/finance-calendar';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, FinanceCalendarComponent],
  templateUrl: './profile.html',
  styleUrl: './profile.css',
})
export class ProfileComponent implements OnInit {

  // ==========================================
  // 1. INJECTIONS
  // ==========================================
  private authService = inject(AuthService);
  private router = inject(Router);
  private contractService = inject(ContractService);
  private incomeService = inject(IncomeService);
  private cdr = inject(ChangeDetectorRef);

  // ==========================================
  // 2. GLOBALE VARIABLEN
  // ==========================================
  email: string = localStorage.getItem('email') || 'Nutzer';
  isLoading: boolean = true;

  // ==========================================
  // 3. CONTRACT (VERTRÄGE) - VARIABLEN & UI-STATE
  // ==========================================
  contracts: Contract[] = [];
  totalCost: number = 0;
  nextDeadlineContract: Contract | null = null;

  // Modals & Ladezustände (Contract)
  selectedContract: any = null;
  contractToReview: Contract | null = null;
  contractToDelete: any = null;

  isSaving: boolean = false;
  isDeleting: boolean = false;
  isEditModalOpen: boolean = false;
  isDeleteModalOpen: boolean = false;

  // ==========================================
  // 4. INCOME (EINKOMMEN) - VARIABLEN & UI-STATE
  // ==========================================
  incomes: Income[] = [];
  newIncome: Income = { amount: 0, source: '' };

  // Modals & Ladezustände (Income)
  selectedIncome: Income | null = null;
  incomeToDelete: Income | null = null;

  isSavingIncome: boolean = false;
  isUpdatingIncome: boolean = false;
  isDeletingIncome: boolean = false;
  isEditIncomeModalOpen: boolean = false;
  isDeleteIncomeModalOpen: boolean = false;

  // ==========================================
  // 5. LIFECYCLE HOOKS
  // ==========================================
  ngOnInit() {
    this.loadMyContracts();
    this.loadIncomes();
  }

  // ==========================================
  // 6. CONTRACT LOGIK & MODALS
  // ==========================================

  loadMyContracts() {
    this.isLoading = true;
    this.contractService.getContracts().subscribe({
      next: (data) => {
        this.contracts = data || [];
        this.calculateTotalCost();
        this.findNextDeadline();
        this.isLoading = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Error loading contracts: ', err);
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  calculateTotalCost() {
    this.totalCost = this.contracts
      .filter(contract => contract.status === 'VERIFIED')
      .reduce((sum, contract) => {
        const cost = Number(contract.monthlyCost) || 0;
        return sum + cost;
      }, 0);
  }

  findNextDeadline() {
    const today = new Date().toISOString().split('T')[0];
    const upcomingContracts = this.contracts
      .filter(contract => contract.status === 'VERIFIED')
      .filter(contract => contract.endDate && contract.endDate !== '2099-12-31' && contract.endDate >= today)
      .sort((a, b) => new Date(a.endDate).getTime() - new Date(b.endDate).getTime());

    this.nextDeadlineContract = upcomingContracts.length > 0 ? upcomingContracts[0] : null;
  }

  // --- Contract Review ---
  openReviewModal(contract: Contract) {
    this.contractToReview = { ...contract };
  }

  closeReviewModal() {
    this.contractToReview = null;
  }

  saveContractReview() {
    if (this.contractToReview && this.contractToReview.id) {
      this.isSaving = true;
      this.contractService.updateContract(this.contractToReview.id, this.contractToReview).subscribe({
        next: () => {
          this.loadMyContracts();
          this.closeReviewModal();
          this.isLoading = false;
        },
        error: (err) => {
          console.error("Contract Register is Fail", err);
          this.isLoading = false;
        }
      });
    }
  }

  // --- Contract Edit ---
  openEditModal(contract: any) {
    this.selectedContract = { ...contract };
    this.isEditModalOpen = true;
  }

  closeEditModal() {
    this.isEditModalOpen = false;
    this.selectedContract = null;
  }

  saveContract() {
    if (!this.selectedContract) return;

    this.isSaving = true;
    this.selectedContract.status = 'VERIFIED';

    this.contractService.updateContract(this.selectedContract.id, this.selectedContract).subscribe({
      next: (updatedContract) => {
        const index = this.contracts.findIndex(c => c.id === updatedContract.id);
        if (index !== -1) {
          this.contracts[index] = updatedContract;
        }
        this.calculateTotalCost();
        this.findNextDeadline();
        this.isSaving = false;
        this.closeEditModal();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Fehler beim Aktualisieren des Vertrags:', err);
        this.isSaving = false;
        this.cdr.detectChanges();
        alert('Huch! Die Änderungen konnten nicht gespeichert werden. Server-Verbindung prüfen.');
      }
    });
  }

  // --- Contract Delete (Modern Modal) ---
  openDeleteModal(contract: any) {
    this.contractToDelete = contract;
    this.isDeleteModalOpen = true;
  }

  closeDeleteModal() {
    this.isDeleteModalOpen = false;
    this.contractToDelete = null;
  }

  confirmDelete() {
    if (!this.contractToDelete) return;

    this.isDeleting = true;
    this.contractService.deleteContract(this.contractToDelete.id).subscribe({
      next: () => {
        this.contracts = this.contracts.filter(c => c.id !== this.contractToDelete.id);
        this.calculateTotalCost();
        this.findNextDeadline();
        this.isDeleting = false;
        this.closeDeleteModal();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Fehler beim Löschen des Vertrags:', err);
        this.isDeleting = false;
        this.closeDeleteModal();
        this.cdr.detectChanges();
        alert('Huch! Der Vertrag konnte nicht gelöscht werden. Bitte versuche es später noch einmal.');
      }
    });
  }

  // --- Contract Delete (Legacy Window Confirm) ---
  deleteContract(id: number | undefined, providerName: string) {
    if (!id) return;

    const isConfirmed = window.confirm(`Möchtest du den Vertrag von "${providerName}" wirklich löschen? Das kann nicht rückgängig gemacht werden.`);
    if (isConfirmed) {
      this.contractService.deleteContract(id).subscribe({
        next: () => {
          console.log('Contract deleted');
          this.loadMyContracts();
        },
        error: (err) => {
          console.error('Error deleting contracts: ', err);
          alert('Failed to delete');
        }
      });
    }
  }

  // ==========================================
  // 7. INCOME LOGIK & MODALS
  // ==========================================

  loadIncomes() {
    this.incomeService.getIncomes().subscribe({
      next: (data) => {
        this.incomes = data;
        this.cdr.detectChanges();
      },
      error: (err) => console.error('Fehler beim Laden der Einkommen', err)
    });
  }

  saveIncome() {
    // Sauberer Check ohne verschachteltes If
    if(this.newIncome.amount)
    if (!this.newIncome.source || this.newIncome.amount <= 0) return;

    this.isSavingIncome = true;

    this.incomeService.addIncome(this.newIncome).subscribe({
      next: (savedIncome) => {
        this.incomes.push(savedIncome);
        this.newIncome = { amount: 0, source: '' };
        this.isSavingIncome = false;
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Fehler beim Speichern des Einkommens', err);
        this.isSavingIncome = false;
        this.cdr.detectChanges();
      }
    });
  }

  // --- Income Edit ---
  openEditIncomeModal(income: Income) {
    this.selectedIncome = { ...income };
    this.isEditIncomeModalOpen = true;
  }

  closeEditIncomeModal() {
    this.isEditIncomeModalOpen = false;
    this.selectedIncome = null;
  }

  updateIncome() {
    if (!this.selectedIncome || !this.selectedIncome.id) return;

    this.isUpdatingIncome = true;

    this.incomeService.updateIncome(this.selectedIncome.id, this.selectedIncome).subscribe({
      next: (updatedIncome) => {
        const index = this.incomes.findIndex(i => i.id === updatedIncome.id);
        if (index !== -1) {
          this.incomes[index] = updatedIncome;
        }
        this.isUpdatingIncome = false;
        this.closeEditIncomeModal();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Fehler beim Aktualisieren des Einkommens:', err);
        this.isUpdatingIncome = false;
        this.cdr.detectChanges();
      }
    });
  }

  // --- Income Delete ---
  openDeleteIncomeModal(income: Income) {
    this.incomeToDelete = income;
    this.isDeleteIncomeModalOpen = true;
  }

  closeDeleteIncomeModal() {
    this.isDeleteIncomeModalOpen = false;
    this.incomeToDelete = null;
  }

  confirmDeleteIncome() {
    if (!this.incomeToDelete || !this.incomeToDelete.id) return;

    this.isDeletingIncome = true;

    this.incomeService.deleteIncome(this.incomeToDelete.id).subscribe({
      next: () => {
        this.incomes = this.incomes.filter(i => i.id !== this.incomeToDelete!.id);
        this.isDeletingIncome = false;
        this.closeDeleteIncomeModal();
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Fehler beim Löschen des Einkommens:', err);
        this.isDeletingIncome = false;
        this.cdr.detectChanges();
      }
    });
  }

  // ==========================================
  // 8. AUTHENTICATION
  // ==========================================

  logout() {
    this.authService.logout();
    this.router.navigate(['login']);
  }
}
