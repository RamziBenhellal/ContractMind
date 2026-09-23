package com.ramzi.backend.controller;

import com.ramzi.backend.dto.BankAccountDto;
import com.ramzi.backend.dto.SyncNowResponse;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.service.BankAccountService;
import com.ramzi.backend.service.BankAccountSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@CrossOrigin(origins = "http://localhost:4200")
@RestController
@RequestMapping({"/api/bank-accounts", "/bank-accounts"})
@RequiredArgsConstructor
public class BankAccountController {
    private final BankAccountService bankAccountService;
    private final BankAccountRepository bankAccountRepository;
    private final BankAccountSyncService bankAccountSyncService;

    @GetMapping
    public ResponseEntity<List<BankAccountDto>> getBankAccounts(Principal principal) {
        String email = principal.getName();
        List<BankAccountDto> bankAccounts = bankAccountService.getAllBankAcountsForUser(email);
        return ResponseEntity.ok(bankAccounts);
    }

    @PostMapping("/add")
    public ResponseEntity<BankAccountDto> addBankAccount(@RequestBody BankAccountDto bankAccountDto,Principal principal) {
        String email = principal.getName();
        BankAccountDto bankAccount = bankAccountService.addBankAccount(email, bankAccountDto);
        return ResponseEntity.ok(bankAccount);

    }

    @PutMapping("/{id}")
    public ResponseEntity<BankAccountDto> updateBankAccount(@PathVariable Long id, @RequestBody BankAccountDto bankAccountDto) {
        BankAccountDto bankAccount = bankAccountService.EditBankAccount(id, bankAccountDto);
        return ResponseEntity.ok(bankAccount);
    }
    @PutMapping("/{id}/record-balance")
    public ResponseEntity<BankAccountDto> recordBalance(
            @PathVariable Long id,
            @RequestBody RecordBalanceRequest request) {

        BankAccountDto updatedAccount = bankAccountService.recordBalance(id, request.date(), request.balance());
        return ResponseEntity.ok(updatedAccount);
    }
    record RecordBalanceRequest(LocalDate date, BigDecimal balance) {}

    @PostMapping("/{id}/sync-now")
    public ResponseEntity<SyncNowResponse> syncNow(@PathVariable Long id, Principal principal) {
        return ResponseEntity.ok(bankAccountSyncService.syncNow(principal.getName(), id));
    }

    @DeleteMapping("{id}")
    public ResponseEntity<Void> deleteBankAccount(@PathVariable Long id) {
        bankAccountRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
