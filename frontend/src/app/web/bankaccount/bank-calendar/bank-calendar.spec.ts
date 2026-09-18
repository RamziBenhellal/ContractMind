import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { BankCalendarComponent } from './bank-calendar';

describe('BankCalendarComponent', () => {
  let component: BankCalendarComponent;
  let fixture: ComponentFixture<BankCalendarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BankCalendarComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(BankCalendarComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
