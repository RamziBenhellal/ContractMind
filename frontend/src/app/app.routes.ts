import { Routes } from '@angular/router';
import { LoginComponent } from './login/login';
import { RegisterComponent } from './register/register';
import { DashboardComponent } from './dashboard/dashboard';
import { authGuard } from './auth-guard';
import { AddContractComponent } from './contract/add-contract/add-contract';


export const routes: Routes = [
  {path: 'login', component: LoginComponent },
  {path: 'register', component: RegisterComponent },
  {path: 'dashboard', component: DashboardComponent, canActivate: [authGuard]},
  {path: 'add-contract', component: AddContractComponent, canActivate: [authGuard] },
  {path:'', redirectTo: 'dashboard', pathMatch: 'full'},
];
