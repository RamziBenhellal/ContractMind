package com.ramzi.backend.repository;

import com.ramzi.backend.entity.BankAccount;
import com.ramzi.backend.entity.BankTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {

    boolean existsByBankAccountAndExternalId(BankAccount bankAccount, String externalId);
}
