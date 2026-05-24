import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth';
import { ChatComponent } from './chat/chat';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive,ChatComponent],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App {

  public authService = inject(AuthService);
  protected readonly title = signal('frontend');
  private router = inject(Router);



  logout(){
    this.authService.logout();
    this.router.navigate(['login']);
  }
}
