import { Component, inject, OnInit, ChangeDetectorRef } from '@angular/core';
import { Router } from '@angular/router';
import {CommonModule} from '@angular/common';
import {AuthService} from '../auth';
import {Contract, ContractService } from '../contract';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [FormsModule,CommonModule],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css',
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private router = inject(Router);
  private contractService = inject(ContractService);
  private cdr = inject(ChangeDetectorRef);


  email: string = localStorage.getItem('email') || 'Nutzer';

  contracts: Contract[] = [];
  totalCost: number = 0;
  nextDeadlineContract: Contract | null = null;
  isLoading: boolean = true;

  contractToReview: Contract | null = null
  isSaving: boolean = false;

  isDeleteModalOpen = false;
  contractToDelete: any = null;
  isDeleting = false;

  ngOnInit(){
    this.loadMyContracts()
  }



  loadMyContracts() {
    this.isLoading = true; // Startet das Laden

    this.contractService.getContracts().subscribe({
      next: (data) => {
        this.contracts = data || [];
        this.contracts.forEach(contract => {
          console.log(contract.status);
        })
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
    })
  }

  calculateTotalCost(){
    this.totalCost = this.contracts
      .filter(contract => contract.status === 'VERIFIED')
      .reduce((sum, contract) => {
      // Wandelt den Wert sicher in eine Zahl um. Wenn er null/ungültig ist, nimm 0.
      const cost = Number(contract.monthlyCost) || 0;
      return sum + cost;
    }, 0);  }

  findNextDeadline() {
    // Aktuelles Datum im Format "YYYY-MM-DD" holen (z.B. "2026-05-17")
    const today = new Date().toISOString().split('T')[0];

    const upcomingContracts = this.contracts
      .filter(contract => contract.status === 'VERIFIED')
      .filter(contract => contract.endDate && contract.endDate !== '2099-12-31' && contract.endDate >= today)
      .sort((a, b) => new Date(a.endDate).getTime() - new Date(b.endDate).getTime());

    if (upcomingContracts.length > 0) {
      this.nextDeadlineContract = upcomingContracts[0];
    } else {
      this.nextDeadlineContract = null;
    }
  }

    openReviewModal(contract: Contract){
      this.contractToReview = {... contract};
    }

    closeReviewModal(){
      this.contractToReview = null;
    }

    deleteContract(id: number | undefined, providerName: string){
    if(!id) return;

    const isConfirmed = window.confirm(`Möchtest du den Vertrag von "${providerName}" wirklich löschen? Das kann nicht rückgängig gemacht werden.`);
    if(isConfirmed){
      this.contractService.deleteContract(id).subscribe({
        next: () => {
          console.log('Contract deleted');
          this.loadMyContracts();
        },
        error: (err) => {
          console.error('Error deleting contracts: ', err);
          alert('Failed to delete');
        }
      })
    }
    }

    saveContractReview(){
      if(this.contractToReview && this.contractToReview.id){
        this.isSaving = true;
        this.contractService.updateContract(this.contractToReview.id, this.contractToReview)
        .subscribe({
          next: (updatedContract) => {
            this.loadMyContracts();
            this.closeReviewModal();
            this.isLoading = false
          },
          error: (err) => {
            console.error("Contract Register is Fail");
            this.isLoading = false;
          }
        })
      }
    }
  isEditModalOpen = false;
  selectedContract: any = null;

  // Methoden für das Pop-up
  openEditModal(contract: any) {
    // Wir machen eine Kopie des Vertrags, damit Änderungen erst beim "Speichern" übernommen werden
    this.selectedContract = { ...contract };
    this.isEditModalOpen = true;
  }

  closeEditModal() {
    this.isEditModalOpen = false;
    this.selectedContract = null;
  }


  saveContract() {
    if (!this.selectedContract) return;

    // 1. Lade-Animation starten und Status auf VERIFIED setzen
    this.isSaving = true;
    this.selectedContract.status = 'VERIFIED';

    // 2. Den PUT-Request an das Spring Boot Backend senden
    this.contractService.updateContract(this.selectedContract.id, this.selectedContract).subscribe({
      next: (updatedContract) => {
        // Erfolgsfall: Das Backend schickt den aktualisierten Vertrag zurück

        // 3. Den Vertrag im lokalen Array durch den vom Server ersetzen
        const index = this.contracts.findIndex(c => c.id === updatedContract.id);
        if (index !== -1) {
          this.contracts[index] = updatedContract;
        }

        // 4. Statistiken (Gesamtkosten etc.) neu berechnen
        this.calculateTotalCost();
        this.findNextDeadline();

        // 5. Pop-up schließen und Ladezustand beenden
        this.isSaving = false;
        this.closeEditModal();

        // 🚨 Angular zwingen, das UI sofort zu aktualisieren
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error('Fehler beim Aktualisieren des Vertrags:', err);

        // Im Fehlerfall: Ladezustand beenden, aber Modal offen lassen,
        // damit der Nutzer die Daten korrigieren kann.
        this.isSaving = false;
        this.cdr.detectChanges();

        alert('Huch! Die Änderungen konnten nicht gespeichert werden. Server-Verbindung prüfen.');
      }
    });
  }

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

  logout(){
    this.authService.logout();
    this.router.navigate(['login']);
  }

}
