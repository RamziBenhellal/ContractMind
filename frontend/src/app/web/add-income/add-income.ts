import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { Income, IncomeService } from '../../service/income/income';

@Component({
  selector: 'app-income-widget',
  standalone:true,
  imports: [FormsModule,RouterModule,CommonModule],
  templateUrl: './add-income.html',
  styleUrl: './add-income.css',
})
export class AddIncomeCoponent {

private incomeService = inject(IncomeService);
private router = inject(Router)

newIncome: Income = {
    amount: 0,
    source: ''
  };

  isSaving: boolean = false;

saveIncome(){
  this.isSaving = true;
  this.incomeService.addIncome(this.newIncome).subscribe({
    next: () =>{
      this.isSaving = false;
      this.router.navigate(['/dashboard'])
    },
    error: err  => {
      console.error('Error while saving');
      this.isSaving = false;
      alert('Saving Error');

    }
  })
}


}
