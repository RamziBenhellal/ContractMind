import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AddIncomeCoponent } from './add-income';

describe('IncomeWidget', () => {
  let component: AddIncomeCoponent;
  let fixture: ComponentFixture<AddIncomeCoponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddIncomeCoponent],
    }).compileComponents();

    fixture = TestBed.createComponent(AddIncomeCoponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
