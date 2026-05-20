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


  logout(){
    this.authService.logout();
    this.router.navigate(['login']);
  }

}
