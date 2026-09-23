import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { AddContractComponent } from './add-contract';

describe('AddContractComponent', () => {
  let component: AddContractComponent;
  let fixture: ComponentFixture<AddContractComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddContractComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(AddContractComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
