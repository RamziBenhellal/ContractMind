import { Component, inject, ChangeDetectorRef, ViewChild, ElementRef, AfterViewChecked } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ChatService } from '../chat';
import { Router } from '@angular/router';

interface Message{
  text: string;
  sender: 'user'|'bot';
}
@Component({
  selector: 'app-chat',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})


export class ChatComponent implements AfterViewChecked {
  private chatService: ChatService= inject(ChatService);
  private router = inject(Router);

  isOpen = false;
  isLoading= false;
  currentInput= '';

  messages: Message[] = [
    {text:'Hallo! Ich bin dein ContractMind Assistent. Frag mich einfach etwas zu deinen Verträgen!', sender: 'bot'}
  ];

  private cdr = inject(ChangeDetectorRef);
  @ViewChild('scrollMe') private myScrollContainer!: ElementRef;

  toggleChat(){
    this.isOpen = !this.isOpen;
  }

  ngAfterViewChecked() {
    this.scrollToBottom();
  }

  scrollToBottom(): void {
    try {
      this.myScrollContainer.nativeElement.scrollTop = this.myScrollContainer.nativeElement.scrollHeight;
    } catch(err) { }
  }

  sendMessage(){
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
