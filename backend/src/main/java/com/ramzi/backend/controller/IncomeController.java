package com.ramzi.backend.controller;

import com.ramzi.backend.dto.ContractDto;
import com.ramzi.backend.dto.IncomeDto;
import com.ramzi.backend.entity.Income;
import com.ramzi.backend.repository.IncomeRepository;
import com.ramzi.backend.service.IncomeService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping("/api/incomes")
@AllArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;
    private final IncomeRepository incomeRepository;

    @GetMapping
    public ResponseEntity<List<IncomeDto>> getMyIncome(Principal principal) {
        String email = principal.getName();
        List<IncomeDto> myIncomes = incomeService.getAllIncomesForUser(email);
        return ResponseEntity.ok(myIncomes);
    }

    @PostMapping("/add")
    public ResponseEntity<IncomeDto> addIncome(@RequestBody IncomeDto incomeDto, Principal principal) {
        String email = principal.getName();
        incomeService.addIncomeManually(email,incomeDto);
        return ResponseEntity.ok(incomeDto);
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncomeDto> updateIncome(
            @PathVariable Long id,
            @RequestBody IncomeDto updatedData
    ){
        IncomeDto incomeDto = incomeService.editIncome(id, updatedData);
        return ResponseEntity.ok(incomeDto);
    }

    @DeleteMapping("{id}")
    ResponseEntity<Void> deleteIncome(@PathVariable Long id) {
        incomeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
