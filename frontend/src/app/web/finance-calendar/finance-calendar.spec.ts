import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { FinanceCalendarComponent } from './finance-calendar';

describe('FinanceCalendarComponent', () => {
  let component: FinanceCalendarComponent;
  let fixture: ComponentFixture<FinanceCalendarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FinanceCalendarComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(FinanceCalendarComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
