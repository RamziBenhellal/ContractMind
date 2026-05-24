import { AfterViewChecked, ChangeDetectorRef, Component, ElementRef, inject, ViewChild } from '@angular/core';
import { ChatService } from '../chat';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface Message { text: string; sender: 'user' | 'bot'; }

@Component({
  selector: 'app-assistant',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './assistant.html',
  styleUrls: ['./assistant.css']
})
export class AssistantComponent implements AfterViewChecked{
  isLoading = false;
  currentInput = '';

  messages: Message[] = [
    { text: 'Willkommen in deinem persönlichen Vertrags-Hub! Wie kann ich dir heute helfen? Möchtest du wissen, wo du sparen kannst?', sender: 'bot' }
  ];

  private chatService = inject(ChatService);

  private cdr = inject(ChangeDetectorRef);
  @ViewChild('scrollMe') private myScrollContainer!: ElementRef;


  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      this.myScrollContainer.nativeElement.scrollTop = this.myScrollContainer.nativeElement.scrollHeight;
    } catch(err) { }
  }

  sendMessage() {
    if (!this.currentInput.trim()) return;

    const userMsg = this.currentInput;

    // 🆕 TRICK 1: Array neu zuweisen (Immutable Update), hilft Angular Änderungen zu erkennen
    this.messages = [...this.messages, { text: userMsg, sender: 'user' }];
    this.currentInput = '';
    this.isLoading = true;

    this.chatService.askQuestion(userMsg).subscribe({
      next: (res) => {
        // Array erneut neu zuweisen
        this.messages = [...this.messages, { text: res.answer, sender: 'bot' }];
        this.isLoading = false;

        // 🆕 TRICK 2: Wir sagen Angular EXPLIZIT: "Bitte UI sofort neu rendern!"
        this.cdr.detectChanges();
      },
      error: (err) => {
        console.error(err);
        this.messages = [...this.messages, { text: 'Verbindungsfehler zur KI.', sender: 'bot' }];
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }
}
