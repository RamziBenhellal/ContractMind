import { Routes } from '@angular/router';
import { LoginComponent } from './web/login/login';
import { RegisterComponent } from './web/register/register';
import { DashboardComponent } from './web/dashboard/dashboard';
import { authGuard } from './service/auth/auth-guard';
import { AddContractComponent } from './web/contract/add-contract/add-contract';
import { AssistantComponent } from './web/assistant/assistant';
import { AddIncomeCoponent } from './web/add-income/add-income';
import { ProfileComponent } from './web/profile/profile';
import { BankCalendarViewComponent } from './web/bankaccount/bank-calendar-view/bank-calendar-view';
import { BankConnectionWizardComponent } from './web/bankaccount/bank-connection-wizard/bank-connection-wizard';
import { TransactionReviewComponent } from './web/bankaccount/transaction-review/transaction-review';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'add-contract', component: AddContractComponent, canActivate: [authGuard] },
  { path: 'add-income', component: AddIncomeCoponent, canActivate: [authGuard] },
  { path: 'chat', component: AssistantComponent, canActivate: [authGuard] },
  { path: 'profile', component: ProfileComponent, canActivate: [authGuard] },
  { path: 'bank-calendar', component: BankCalendarViewComponent, canActivate: [authGuard] },
  { path: 'transaction-review', component: TransactionReviewComponent, canActivate: [authGuard] },
  { path: 'bank-connect', component: BankConnectionWizardComponent, canActivate: [authGuard] },
  { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
];
