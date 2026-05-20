import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { from } from 'rxjs';
import { Router,RouterModule} from '@angular/router';
import { Contract, ContractService } from '../../contract';
import { FormsModule } from '@angular/forms';


@Component({
  selector: 'app-add-contract',
  standalone: true,
  imports: [FormsModule,CommonModule,RouterModule],
  templateUrl: './add-contract.html',
  styleUrl: './add-contract.css',
})
export class AddContractComponent {
  private contractService = inject(ContractService);
  private router = inject(Router);

  entryMode: 'upload' |  'manual' = 'upload';

  newManualContract: Contract = {
    provider: '',
    contractType: '',
    monthlyCost: 0,
    endDate: '2099-12-31', // Standardmäßig auf "unbefristet"
    status: 'VERIFIED'
  }

  isSaving: boolean = false;


  selectedFile: File | null = null;
  errorMessage: string = '';
  isUploading: boolean = false;

  onFileSelected(event:any) {
    const file = event.target.files[0];
    if (file && file.type === 'application/pdf') {
      this.selectedFile = file;
      this.errorMessage = '';
    }
    else {
      this.errorMessage = 'please upload a pdf file';
      this.selectedFile = null;
    }
  }

  saveManualContract(){
    this.isSaving = true;
    this.contractService.addContract(this.newManualContract).subscribe({
      next: () => {
        console.log('Contract is added successfully');
        this.isSaving = false;
        this.router.navigate(['/dashboard']);
      },
      error: err => {
        console.error('Error while saving manual contract.',err);
        this.isSaving = false;
        alert('Failed to add manual contract.');
      }
    })
  }

  onSubmit() {
    if(this.selectedFile){
      this.isUploading = true;
      this.contractService.uploadContract(this.selectedFile).subscribe({
        next: (response) => {
          this.router.navigate(['/dashboard']);
        },
        error: (err) => {
          this.errorMessage = 'Failed to upload';
          this.isUploading = false;
        }
      })
    }
  }

}
