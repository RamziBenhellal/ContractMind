import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { AssistantComponent } from './assistant';

describe('AssistantComponent', () => {
  let component: AssistantComponent;
  let fixture: ComponentFixture<AssistantComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AssistantComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(AssistantComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
