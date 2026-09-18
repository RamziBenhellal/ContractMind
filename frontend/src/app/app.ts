import { Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './service/auth/auth';
import { ChatComponent } from './web/chat/chat';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

const SIDEBAR_COLLAPSED_KEY = 'contractmind.sidebar.collapsed';

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

  protected readonly navItems: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: '📊' },
    { path: '/chat', label: 'Assistant', icon: '✨' },
    { path: '/bank-calendar', label: 'Bank-Kalender', icon: '🏦' },
    { path: '/bank-connect', label: 'Bank verbinden', icon: '🔗' },
    { path: '/add-income', label: 'Neues Einkommen', icon: '💰' },
    { path: '/add-contract', label: 'Neuer Vertrag', icon: '➕' },
    { path: '/profile', label: 'Profil', icon: '👤' },
  ];

  protected readonly collapsed = signal(localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true');
  protected readonly mobileOpen = signal(false);

  toggleCollapsed() {
    this.collapsed.update(value => !value);
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(this.collapsed()));
  }

  toggleMobile() {
    this.mobileOpen.update(value => !value);
  }

  closeMobile() {
    this.mobileOpen.set(false);
  }

  logout(){
    this.closeMobile();
    this.authService.logout();
    this.router.navigate(['login']);
  }
}
