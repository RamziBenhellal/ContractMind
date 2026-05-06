import { Component, inject } from '@angular/core';
import {CommonModule} from '@angular/common';
import {ReactiveFormsModule, FormBuilder, FormGroup, Validators} from '@angular/forms';
import {Router, RouterModule} from '@angular/router';
import {AuthService} from '../auth';
import { appConfig } from '../app.config';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './register.html',
  styleUrl: './register.css',
})
export class RegisterComponent {
  private fb = inject(FormBuilder);
  private authService = inject(AuthService);
  private router = inject(Router);

  registerForm: FormGroup = this.fb.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(6)]],
  });

  errorMessage: string = '';
  successMessage: string = '';

  onSubmit() {
    if (this.registerForm.valid) {
      this.authService.register(this.registerForm.value).subscribe({
        next: (response) => {
          this.successMessage = 'Register successfully ! redirected to Login ...  ';
          this.errorMessage = '';

          setTimeout(() => this.router.navigateByUrl('/login'), 2000);
        },
        error: (err) => {
          this.errorMessage = 'Failed to register ' + (err.error?.message || 'unknown error');
          this.successMessage = '';
        }
      });
    } else {
      this.errorMessage = 'Please fill all fields.';
    }
  }
}
