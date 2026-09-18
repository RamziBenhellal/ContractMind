package com.ramzi.backend.service;

import com.ramzi.backend.dto.ContractDto;
import com.ramzi.backend.dto.IncomeDto;
import com.ramzi.backend.entity.Contract;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.IncomeRepository;
import com.ramzi.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class IncomeService {

    private IncomeRepository incomeRepository;
    private UserRepository userRepository;

    public List<IncomeDto> getAllIncomesForUser(String email) {
        List<Income> incomes = incomeRepository.findByUser_Email(email);
        return incomes.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public IncomeDto addIncomeManually(String email,IncomeDto incomeDto) {

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Nutzer nicht gefunden"));

        Income newIncome = new Income();
        newIncome.setAmount(incomeDto.getAmount());
        newIncome.setSource(incomeDto.getSource());
        newIncome.setPaydayOfMonth(incomeDto.getPaydayOfMonth());
        newIncome.setUser(currentUser);

        Income income = incomeRepository.save(newIncome);
        return mapToDto(income);
    }

    public IncomeDto editIncome(Long id, IncomeDto updatedIncome) {
        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income not found"));
        income.setAmount(updatedIncome.getAmount());
        income.setSource(updatedIncome.getSource());
        income.setPaydayOfMonth(updatedIncome.getPaydayOfMonth());

        Income savedIncome =  incomeRepository.save(income);

        return mapToDto(savedIncome);
    }

    private IncomeDto mapToDto(Income income) {
        return IncomeDto.builder()
                .id(income.getId())
                .amount(income.getAmount())
                .source(income.getSource())
                .paydayOfMonth(income.getPaydayOfMonth())
                .build();
    }

}
