package com.ramzi.backend.service;

import com.ramzi.backend.dto.BankAccountDto;
import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.User;
import com.ramzi.backend.repository.BankAccountRepository;
import com.ramzi.backend.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class BankAccountService {
    private BankAccountRepository bankAccountRepository;
    private UserRepository UserRepository;

    public List<BankAccountDto> getAllBankAcountsForUser(String email){
        List<BankAccount> bankAccounts = bankAccountRepository.findByUser_Email(email);
        return bankAccounts.stream().map(this::mapToBankAccountDto).collect(Collectors.toList());
    }

    public BankAccountDto addBankAccount(String email, BankAccountDto bankAccountDto){
        User user = UserRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        BankAccount newBankAccount = new BankAccount();
        newBankAccount.setAccountName(bankAccountDto.getAccountName());
        newBankAccount.setBankName(bankAccountDto.getBankName());
        newBankAccount.setIban(bankAccountDto.getIban());
        newBankAccount.setAccountType(bankAccountDto.getAccountType());
        newBankAccount.setBalance(bankAccountDto.getBalance());
        newBankAccount.setUser(user);

        BankAccount bankAccount = bankAccountRepository.save(newBankAccount);
        return mapToBankAccountDto(bankAccount);
    }

    public BankAccountDto EditBankAccount(Long id, BankAccountDto bankAccountDto){
        BankAccount bankAccount = bankAccountRepository.findById(bankAccountDto.getId())
                .orElseThrow(() -> new RuntimeException("BankAccount not found"));
        bankAccount.setAccountName(bankAccountDto.getAccountName());
        bankAccount.setBankName(bankAccountDto.getBankName());
        bankAccount.setIban(bankAccountDto.getIban());
        if (bankAccountDto.getAccountType() != null) {
            bankAccount.setAccountType(bankAccountDto.getAccountType());
        }
        if (bankAccountDto.getBalance() != null) {
            bankAccount.setBalance(bankAccountDto.getBalance());
        }

        BankAccount savedBankAccount = bankAccountRepository.save(bankAccount);
        return mapToBankAccountDto(savedBankAccount);
    }

    @Transactional
    public BankAccountDto recordBalance(Long accountId, LocalDate date, BigDecimal balance){
        BankAccount bankAccount = bankAccountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("BankAccount not found"));
        LocalDate entryDate = (date != null) ? date : LocalDate.now();

        bankAccount.getBalanceHistory().put(entryDate, balance);

        return mapToBankAccountDto(bankAccount);
    }

    private BankAccountDto mapToBankAccountDto(BankAccount bankAccount) {
        return BankAccountDto.builder()
                .id(bankAccount.getId())
                .accountName(bankAccount.getAccountName())
                .bankName(bankAccount.getBankName())
                .iban(bankAccount.getIban())
                .accountType(bankAccount.getAccountType())
                .balance(bankAccount.getBalance())
                .lastSyncedAt(bankAccount.getLastSyncedAt())
                .balanceHistory(bankAccount.getBalanceHistory())
                .build();
    }

}
